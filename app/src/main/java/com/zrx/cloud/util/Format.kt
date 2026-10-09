package com.zrx.cloud.util

import java.util.Locale

object Format {
    fun bytes(n: Long): String {
        if (n <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = n.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${v.toLong()} ${units[i]}"
        else String.format(Locale.US, "%.2f %s", v, units[i])
    }
}
