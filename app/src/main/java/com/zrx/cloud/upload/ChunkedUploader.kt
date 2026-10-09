package com.zrx.cloud.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.zrx.cloud.data.ApiException
import com.zrx.cloud.data.NetDiskApi
import com.zrx.cloud.util.Format
import com.zrx.cloud.util.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * 分片上传器：与网页版 assets/upload.js 同一套协议。
 *
 *   limits → init（断点续传：服务端返回已落盘分片）→ 4 个并发 worker 逐片上传
 *   （单片失败指数退避重试）→ merge（合并被占用时轮询等待）
 *
 * upload_id 用 名称|大小|0|目录 的 SHA-256 前 32 位：
 * SAF 拿不到可靠的 lastModified，固定为 0，同一文件 + 同一目录 id 稳定 → 断点续传有效。
 */
class ChunkedUploader(
    private val api: NetDiskApi,
    private val contentResolver: ContentResolver,
) {

    suspend fun upload(
        uri: Uri,
        displayName: String,
        dir: String,
        onProgress: (done: Long, total: Long) -> Unit,
    ): String {
        val size = querySize(uri)
        val limits = api.limits()
        if (!limits.usable) {
            throw ApiException("服务器上传上限（post_max_size）过小，连最小分片都收不下")
        }
        if (size > limits.maxFileSize) {
            throw ApiException("超过单文件上限 ${Format.bytes(limits.maxFileSize)}")
        }
        val chunkSize = limits.chunkSize
        val id = Sha256.of("$displayName|$size|0|$dir").take(32)

        val init = api.uploadInit(id, dir, displayName, size, chunkSize, 0L)
        if (init.complete) {
            onProgress(size, size)
            return init.finalName ?: displayName
        }

        val confirmed = AtomicLong(init.doneBytes)
        onProgress(min(confirmed.get(), size), size)

        if (init.totalChunks > 0) {
            val missing = (0 until init.totalChunks).filter { it !in init.doneChunks }
            val cursor = AtomicInteger(0)
            coroutineScope {
                repeat(WORKERS) {
                    launch {
                        while (isActive) {
                            val i = cursor.getAndIncrement()
                            if (i >= missing.size) break
                            val idx = missing[i]
                            uploadChunkWithRetry(uri, size, chunkSize, id, idx)
                            confirmed.addAndGet(chunkLen(size, chunkSize, idx).toLong())
                            onProgress(min(confirmed.get(), size), size)
                        }
                    }
                }
            }
        }

        return merge(id, displayName)
    }

    private suspend fun merge(id: String, displayName: String): String {
        repeat(MERGE_RETRIES) {
            val r = api.uploadMerge(id)
            if (r.complete) return r.finalName ?: displayName
            delay(MERGE_WAIT)
        }
        throw ApiException("服务器合并超时，请稍后在文件列表查看")
    }

    private suspend fun uploadChunkWithRetry(uri: Uri, size: Long, chunkSize: Int, id: String, index: Int) {
        var wait = 1000L
        var last: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val bytes = readChunk(uri, size, chunkSize, index)
                api.uploadChunk(id, index, bytes)
                return
            } catch (e: Exception) {
                last = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(8000L)
                }
            }
        }
        throw last ?: ApiException("第 ${index + 1} 个分片上传失败")
    }

    private suspend fun readChunk(uri: Uri, size: Long, chunkSize: Int, index: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val offset = index.toLong() * chunkSize
            val len = chunkLen(size, chunkSize, index)
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                    ch.position(offset)
                    val buf = ByteArray(len)
                    val bb = ByteBuffer.wrap(buf)
                    while (bb.hasRemaining()) {
                        if (ch.read(bb) < 0) break
                    }
                    buf
                }
            } ?: throw ApiException("无法读取所选文件")
        }

    private fun chunkLen(size: Long, chunkSize: Int, index: Int): Int {
        val offset = index.toLong() * chunkSize
        return minOf(chunkSize.toLong(), size - offset).toInt()
    }

    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.SIZE)
            if (i >= 0 && c.moveToFirst() && !c.isNull(i)) return c.getLong(i)
        }
        return 0L
    }

    companion object {
        private const val WORKERS = 4
        private const val MAX_RETRIES = 4
        private const val MERGE_RETRIES = 40
        private const val MERGE_WAIT = 1500L
    }
}
