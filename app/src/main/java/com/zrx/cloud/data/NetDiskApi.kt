package com.zrx.cloud.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 自建网盘服务端接口。
 *
 * 认证：POST index.php 登录，会话 Cookie 由 [CookieStore] 统一保管；
 * 之后每次请求带 Cookie + CSRF 头。接口：api.php，统一 {ok, ...} / {ok:false, error} 结构。
 * 分片上传与网页版同一套协议：limits → init → chunk* → merge。
 *
 * 所有方法都是同步阻塞的，调用方负责切到 Dispatchers.IO。
 */
class NetDiskApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS) // 分片上传不限写超时
        // 必须跟随重定向：不少服务器会把 HTTP 301 跳到 HTTPS，
        // 或给目录补斜杠；不跟随的话登录请求根本没落到服务端，
        // 却会表现成「总是提示密码错误」。
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(Session.cookies)
        .build()

    private fun Request.Builder.withCsrf(): Request.Builder {
        if (Session.csrf.isNotEmpty()) header("X-CSRF-Token", Session.csrf)
        return this
    }

    /* ============================== 登录 ============================== */

    /** 一次请求的结果：状态码 / 响应体 / 跟随跳转后的最终地址 */
    private class Reply(
        val code: Int,
        val body: String,
        val finalUrl: HttpUrl,
        val redirected: Boolean,
    )

    /** 发一个请求；网络异常转成可读提示 */
    private fun send(req: Request): Reply = try {
        client.newCall(req).execute().use { resp ->
            Reply(
                code = resp.code,
                body = resp.body?.string().orEmpty(),
                finalUrl = resp.request.url,
                redirected = resp.priorResponse != null,
            )
        }
    } catch (e: IOException) {
        throw ApiException("连不上服务器：${e.message ?: "网络不可用"}\n请检查地址、端口与网络")
    }

    /**
     * 登录。成功后保存会话 Cookie（由 CookieJar 自动完成）与 CSRF 令牌。
     *
     * 失败时给出可读原因，不再一律说"密码错误"——实测「服务端改过 session 名」
     * 「站点被 301 重定向」「地址填错连到别的站点」这三种情况，
     * 旧实现都会弹出同一句「用户名或密码不正确」，把排查方向带偏。
     */
    fun login(server: String, username: String, password: String) =
        doLogin(server, username, password, allowRetry = true)

    private fun doLogin(server: String, username: String, password: String, allowRetry: Boolean) {
        Session.baseUrl = server
        Session.logout()

        val body = FormBody.Builder()
            .add("do", "login")
            .add("username", username)
            .add("password", password)
            .build()
        val r = send(Request.Builder().url(Session.baseUrl + "/index.php").post(body).build())
        if (r.code >= 400) {
            throw ApiException("服务器返回 HTTP ${r.code}，请检查地址是否填对（端口 / 子目录）")
        }

        // 站点跳转过（最常见的是 HTTP 被强制跳到 HTTPS）：跳转会把 POST 变成 GET，
        // 密码就丢了，所以这里用更正后的地址重试一次。
        if (r.redirected && csrfOf(r.body) == null) {
            val fixed = originOf(r.finalUrl)
            if (allowRetry && fixed != Session.baseUrl) {
                return doLogin(fixed, username, password, allowRetry = false)
            }
            throw ApiException("服务器把登录请求跳转到了：\n$fixed\n请把服务器地址改成这个再登录")
        }
        checkLoggedIn(r.body)
        if (r.redirected) Session.baseUrl = originOf(r.finalUrl)
        Session.username = username
    }

    /** 重新取一次 CSRF 令牌（会话仍有效时） */
    fun refreshCsrf() {
        val r = send(Request.Builder().url(Session.baseUrl + "/index.php").get().build())
        csrfOf(r.body)?.let {
            Session.csrf = it
            return
        }
        if (isLoginPage(r.body)) throw ApiException("登录已失效，请重新登录")
        throw ApiException("服务器返回的不是网盘首页，请检查地址是否正确")
    }

    /** 从 index.php 的地址推出站点根（保留子目录），用于跳转后修正地址 */
    private fun originOf(u: HttpUrl): String {
        val path = u.encodedPath
        val dir = if (path.endsWith("/index.php")) path.removeSuffix("/index.php") else ""
        return u.newBuilder()
            .encodedPath(if (dir.isEmpty()) "/" else "$dir/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    }

    /** 从首页 HTML 取 CSRF；取不到说明没登上，区分原因后抛错 */
    private fun checkLoggedIn(html: String) {
        csrfOf(html)?.let {
            Session.csrf = it
            return
        }
        if (isLoginPage(html)) {
            val msg = Regex("""<div class="err">(.*?)</div>""")
                .find(html)?.groupValues?.get(1)?.trim()
            throw ApiException(msg ?: "用户名或密码不正确")
        }
        if (html.isBlank()) throw ApiException("服务器没有返回内容，请检查地址是否正确")
        throw ApiException(
            "已连上服务器，但返回的不是网盘首页\n" +
                "请确认填写的是网盘所在目录（例如 http://192.168.1.10:8088）"
        )
    }

    private fun csrfOf(html: String): String? =
        Regex("""var CSRF = "([a-f0-9]+)"""").find(html)?.groupValues?.get(1)

    private fun isLoginPage(html: String): Boolean = html.contains("name=\"password\"")

    /* ============================== 通用调用 ============================== */

    fun call(action: String, fields: Map<String, String> = emptyMap()): JsonElement {
        val body = FormBody.Builder().apply {
            add("action", action)
            add("csrf", Session.csrf)
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        return execute(body)
    }

    private fun callArray(action: String, paths: List<String>, extra: Map<String, String> = emptyMap()): JsonElement {
        val body = FormBody.Builder().apply {
            add("action", action)
            add("csrf", Session.csrf)
            extra.forEach { (k, v) -> add(k, v) }
            paths.forEach { add("paths[]", it) }
        }.build()
        return execute(body)
    }

    private fun callMultipart(action: String, fields: Map<String, String>, fileField: String, fileBody: RequestBody): JsonElement {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("action", action)
            .addFormDataPart("csrf", Session.csrf)
        fields.forEach { (k, v) -> b.addFormDataPart(k, v) }
        b.addFormDataPart(fileField, "chunk.part", fileBody)
        return execute(b.build())
    }

    private fun execute(body: RequestBody): JsonElement {
        val req = Request.Builder().url(Session.baseUrl + "/api.php").withCsrf().post(body).build()
        val r = send(req)
        val obj = try {
            json.parseToJsonElement(r.body).jsonObject
        } catch (e: Exception) {
            // 不是 JSON：多半是会话过期后被跳回了登录页
            throw ApiException(
                if (r.code == 401 || isLoginPage(r.body)) "登录已失效，请重新登录"
                else "服务器没有返回有效 JSON（HTTP ${r.code}）"
            )
        }
        if (obj["ok"]?.jsonPrimitive?.boolean != true) {
            val err = obj["error"]?.jsonPrimitive?.contentOrNull
            if (r.code == 401 || err?.contains("会话校验失败") == true) {
                throw ApiException("登录已失效，请重新登录")
            }
            /*
             * 服务端回「未知操作」，几乎只有一个原因：服务器上还是旧版程序。
             * 这种情况把原始报文甩给用户没用（看不出该做什么），
             * 直接说清楚要做什么。v2.2 起服务端会回带版本号，顺手展示出来。
             */
            if (err?.startsWith("未知操作") == true) {
                val ver = obj["server"]?.jsonPrimitive?.contentOrNull
                throw ApiException(
                    if (ver != null) {
                        "服务端 v$ver 不支持该功能\n请把服务器升级到 v2.1 以上（至少替换 api.php）"
                    } else {
                        "服务端版本过旧，不支持该功能\n请把服务器升级到 v2.1 以上（至少替换 api.php）"
                    }
                )
            }
            throw ApiException(err ?: "请求失败（HTTP ${r.code}）")
        }
        return obj
    }

    /* ============================== 文件操作 ============================== */

    fun list(path: String): List<FileItem> {
        val r = call("list", mapOf("path" to path)).jsonObject
        return json.decodeFromJsonElement(ListSerializer(FileItem.serializer()), r["items"]!!)
    }

    fun mkdir(path: String, name: String) {
        call("mkdir", mapOf("path" to path, "name" to name))
    }

    fun delete(paths: List<String>) {
        callArray("delete", paths)
    }

    fun move(paths: List<String>, dest: String) {
        callArray("move", paths, mapOf("dest" to dest))
    }

    fun rename(path: String, name: String) {
        call("rename", mapOf("path" to path, "name" to name))
    }

    /* ============================== 分享 ============================== */

    fun share(path: String): String =
        call("share", mapOf("path" to path)).jsonObject["url"]!!.jsonPrimitive.content

    fun shares(): List<ShareItem> {
        val r = call("shares").jsonObject
        return json.decodeFromJsonElement(ListSerializer(ShareItem.serializer()), r["items"]!!)
    }

    fun unshare(id: Long) {
        call("unshare", mapOf("id" to id.toString()))
    }

    fun unshareAll() {
        call("unshare", mapOf("target" to "all"))
    }

    /* ============================== 分片上传 ============================== */

    fun limits(): Limits {
        val o = call("limits").jsonObject
        return Limits(
            maxFileSize = o["max_file_size"]!!.jsonPrimitive.long,
            chunkSize = o["chunk_size"]!!.jsonPrimitive.int,
            maxChunkSize = o["max_chunk_size"]!!.jsonPrimitive.int,
            minChunkSize = o["min_chunk_size"]!!.jsonPrimitive.int,
            usable = o["usable"]!!.jsonPrimitive.boolean,
        )
    }

    fun uploadInit(id: String, path: String, name: String, size: Long, chunkSize: Int, lastModified: Long): InitResult {
        val o = call(
            "init", mapOf(
                "upload_id" to id,
                "path" to path,
                "file_name" to name,
                "file_size" to size.toString(),
                "chunk_size" to chunkSize.toString(),
                "last_modified" to lastModified.toString(),
            )
        ).jsonObject
        val state = o["state"]!!.jsonObject
        return InitResult(
            resume = o["resume"]?.jsonPrimitive?.boolean ?: false,
            totalChunks = state["total_chunks"]!!.jsonPrimitive.int,
            doneChunks = o["done_chunks"]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet() ?: emptySet(),
            doneBytes = o["done_bytes"]?.jsonPrimitive?.long ?: 0L,
            complete = state["complete"]?.jsonPrimitive?.boolean ?: false,
            finalName = state["final_name"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun uploadChunk(id: String, index: Int, bytes: ByteArray) {
        callMultipart(
            "chunk",
            mapOf("upload_id" to id, "chunk_index" to index.toString()),
            "chunk",
            bytes.toRequestBody("application/octet-stream".toMediaType()),
        )
    }

    fun uploadMerge(id: String): MergeResult {
        val o = call("merge", mapOf("upload_id" to id)).jsonObject
        return MergeResult(
            complete = o["complete"]?.jsonPrimitive?.boolean ?: false,
            finalName = o["final_name"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun uploadAbort(id: String) {
        call("abort", mapOf("upload_id" to id))
    }

    /** 下载地址（登录态，配合 Cookie 请求头使用） */
    fun downloadUrl(path: String): String =
        Session.baseUrl + "/download.php?p=" + path.split('/').joinToString("/") { android.net.Uri.encode(it) }
}
