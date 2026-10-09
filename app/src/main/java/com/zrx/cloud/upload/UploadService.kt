package com.zrx.cloud.upload

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zrx.cloud.R
import com.zrx.cloud.data.NetDiskApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

/**
 * 上传前台服务：队列式逐个上传，进度走通知栏。
 * 全部完成后发 [ACTION_REFRESH] 广播让文件列表刷新。
 */
class UploadService : Service() {

    companion object {
        private const val EXTRA_URIS = "extra_uris"
        private const val EXTRA_DIR = "extra_dir"
        private const val CHANNEL_ID = "uploads"
        private const val NOTIF_PROGRESS = 1001
        private const val NOTIF_RESULT = 1002
        const val ACTION_REFRESH = "com.zrx.cloud.action.REFRESH_LIST"

        fun enqueue(context: Context, uris: List<Uri>, dir: String) {
            val intent = Intent(context, UploadService::class.java).apply {
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
                putExtra(EXTRA_DIR, dir)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private data class Task(val uri: Uri, val dir: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedBlockingQueue<Task>()

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        intent?.getStringArrayListExtra(EXTRA_URIS)?.forEach { s ->
            queue.add(Task(Uri.parse(s), intent.getStringExtra(EXTRA_DIR) ?: ""))
        }
        startForeground(NOTIF_PROGRESS, progressNotification("准备上传…", -1))
        if (!running) processQueue()
        return START_NOT_STICKY
    }

    private fun processQueue() {
        running = true
        scope.launch {
            try {
                while (true) {
                    val task = queue.poll() ?: break
                    uploadOne(task)
                }
            } finally {
                running = false
                if (queue.isNotEmpty()) {
                    processQueue()
                } else {
                    sendBroadcast(Intent(ACTION_REFRESH).setPackage(packageName))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun uploadOne(task: Task) {
        val nm = NotificationManagerCompat.from(this)
        val name = queryDisplayName(task.uri)
        try {
            ChunkedUploader(NetDiskApi(), contentResolver).upload(task.uri, name, task.dir) { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                nm.notify(NOTIF_PROGRESS, progressNotification("$name（$pct%）", pct))
            }
            nm.notify(NOTIF_RESULT, resultNotification("$name 上传完成", success = true))
        } catch (e: Exception) {
            nm.notify(NOTIF_RESULT, resultNotification("$name 上传失败：${e.message}", success = false))
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst() && !c.isNull(i)) return c.getString(i)
        }
        return uri.lastPathSegment ?: "未命名文件"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "文件上传", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun progressNotification(text: String, pct: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, pct < 0)
            .build()
    }

    private fun resultNotification(text: String, success: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (success) R.drawable.ic_upload else R.drawable.ic_file)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
    }
}
