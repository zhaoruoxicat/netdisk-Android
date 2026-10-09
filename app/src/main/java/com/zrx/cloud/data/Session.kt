package com.zrx.cloud.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 会话与服务器配置，落 SharedPreferences。
 * 服务端是 PHP Session（Cookie）+ CSRF 令牌，Cookie 由 [CookieStore] 统一保管。
 */
object Session {
    private const val PREFS = "session"
    private const val KEY_BASE = "base_url"
    private const val KEY_USER = "username"
    private const val KEY_CSRF = "csrf"

    private lateinit var prefs: SharedPreferences

    /** OkHttp 用的 Cookie 保管器；由 App.onCreate 里调 init() 建立 */
    lateinit var cookies: CookieStore
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cookies = CookieStore(prefs)
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BASE, normalize(v)).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER, v).apply()

    var csrf: String
        get() = prefs.getString(KEY_CSRF, "") ?: ""
        set(v) = prefs.edit().putString(KEY_CSRF, v).apply()

    val loggedIn: Boolean
        get() = baseUrl.isNotEmpty() && !cookies.isEmpty

    /** 系统下载器不走 OkHttp，需要手动带 Cookie */
    val cookieHeader: String
        get() = cookies.header

    fun logout() {
        cookies.clear()
        csrf = ""
    }

    /** 用户只填 192.168.1.10:8088 也能用：自动补 http://，去掉结尾斜杠 */
    private fun normalize(url: String): String {
        var u = url.trim()
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        return u.trimEnd('/')
    }
}
