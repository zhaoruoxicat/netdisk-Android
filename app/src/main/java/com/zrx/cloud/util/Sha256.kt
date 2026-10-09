package com.zrx.cloud.util

import java.security.MessageDigest

object Sha256 {
    fun of(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
