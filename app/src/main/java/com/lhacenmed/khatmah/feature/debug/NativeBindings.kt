package com.lhacenmed.khatmah.feature.debug

object NativeBindings {

    external fun renameFileNative(oldPath: String, newName: String): Boolean
    external fun deleteFileOrDirectoryNative(path: String): Boolean
    external fun getFileMetadataNative(path: String): FileMetadata?

    fun rename(old: String, new: String) = renameFileNative(old, new)
    fun delete(path: String)             = deleteFileOrDirectoryNative(path)
    fun metadata(path: String)           = getFileMetadataNative(path)

    init { System.loadLibrary("khatmah_files") }
}