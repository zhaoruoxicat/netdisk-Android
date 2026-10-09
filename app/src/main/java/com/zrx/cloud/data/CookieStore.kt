package com.zrx.cloud.data

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 会话 Cookie 保管。
 *
 * 旧实现是从 Set-Cookie 里硬找 `PHPSESSID=`，只要服务端把 session.name 改过、
 * 或者一次下发多个 Cookie，就会一个都取不到 → 后续请求变成"未登录"，
 * 表现却是「用户名或密码不正确」。
 *
 * 这里交给 OkHttp 的 CookieJar 统一存取：任何 Cookie 名、多个 Cookie、
 * 重定向过程中下发的 Cookie 都能正确保存并自动回传。
 * 只在 SharedPreferences 里做持久化（name=value 逐行存放）。
 */
class CookieStore(private val prefs: SharedPreferences) : CookieJar {

    private val cookies = mutableListOf<Cookie>()

    /** 从本地恢复出来的 name=value，等第一次请求时按目标 host 还原成 Cookie */
    private val restored = mutableListOf<Pair<String, String>>()

    init {
        prefs.getString(KEY, "").orEmpty().lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) restored.add(line.substring(0, i) to line.substring(i + 1))
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name }
            this.cookies.add(c)
        }
        prefs.edit()
            .putString(KEY, this.cookies.joinToString("\n") { "${it.name}=${it.value}" })
            .apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (restored.isNotEmpty()) {
            for ((n, v) in restored) {
                runCatching {
                    cookies.add(Cookie.Builder().name(n).value(v).domain(url.host).path("/").build())
                }
            }
            restored.clear()
        }
        return cookies.filter { it.matches(url) }
    }

    val isEmpty: Boolean
        get() = cookies.isEmpty() && restored.isEmpty()

    /** 系统下载器不走 OkHttp，需要手动带走 Cookie */
    val header: String
        get() = cookies.joinToString("; ") { "${it.name}=${it.value}" }

    fun clear() {
        cookies.clear()
        restored.clear()
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "cookies"
    }
}
