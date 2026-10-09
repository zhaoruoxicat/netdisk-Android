package com.zrx.cloud.data

import kotlinx.serialization.Serializable

@Serializable
data class FileItem(
    val name: String,
    val path: String,
    val dir: Boolean,
    val size: Long = 0,
    val time: String = "",
)

@Serializable
data class ShareItem(
    val id: Long,
    val token: String,
    val url: String,
    val path: String,
    val name: String,
    val dir: Boolean,
    val hits: Long,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    val alive: Boolean,
)

data class Limits(
    val maxFileSize: Long,
    val chunkSize: Int,
    val maxChunkSize: Int,
    val minChunkSize: Int,
    val usable: Boolean,
)

data class InitResult(
    val resume: Boolean,
    val totalChunks: Int,
    val doneChunks: Set<Int>,
    val doneBytes: Long,
    val complete: Boolean,
    val finalName: String?,
)

data class MergeResult(
    val complete: Boolean,
    val finalName: String?,
)

class ApiException(message: String) : Exception(message)
