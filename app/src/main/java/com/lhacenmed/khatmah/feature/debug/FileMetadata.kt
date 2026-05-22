package com.lhacenmed.khatmah.feature.debug

data class FileMetadata(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val permissions: String,
    val inode: Long,
    val blockSize: Long,
    val blockCount: Long
)