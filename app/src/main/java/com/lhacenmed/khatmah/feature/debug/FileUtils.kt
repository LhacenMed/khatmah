package com.lhacenmed.khatmah.feature.debug

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun listDirectoryFiles(dir: File): List<File> {
    val f = dir.listFiles()?.toList() ?: return emptyList()
    return f.sortedWith(
        compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) }
    )
}

fun readableSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.2f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.2f KB".format(bytes / 1024.0)
    else               -> "$bytes bytes"
}

fun formatMetadata(data: FileMetadata): String {
    val df  = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val mod = df.format(Date(data.lastModified))
    return """
        Name: ${data.name}
        Path: ${data.path}
        Size: ${readableSize(data.size)}
        Type: ${if (data.isDirectory) "Directory" else "File"}
        Last Modified: $mod
        Permissions: ${data.permissions}
        Inode: ${data.inode}
        Block Size: ${data.blockSize} bytes
        Blocks: ${data.blockCount}
    """.trimIndent()
}