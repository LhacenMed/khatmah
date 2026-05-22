#include <jni.h>
#include <string>
#include <cstdio>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/types.h>
#include <pwd.h>
#include <grp.h>
#include <android/log.h>

#define LOG_TAG "NativeFileOps"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

std::string getPermissionsMode(mode_t mode) {
    std::string perms;
    perms += (mode & S_IRUSR) ? "r" : "-";
    perms += (mode & S_IWUSR) ? "w" : "-";
    perms += (mode & S_IXUSR) ? "x" : "-";
    perms += (mode & S_IRGRP) ? "r" : "-";
    perms += (mode & S_IWGRP) ? "w" : "-";
    perms += (mode & S_IXGRP) ? "x" : "-";
    perms += (mode & S_IROTH) ? "r" : "-";
    perms += (mode & S_IWOTH) ? "w" : "-";
    perms += (mode & S_IXOTH) ? "x" : "-";
    return perms;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lhacenmed_khatmah_feature_debug_NativeBindings_renameFileNative(
        JNIEnv* env, jobject, jstring oldPath, jstring newName) {
    const char* old_path_c = env->GetStringUTFChars(oldPath, nullptr);
    const char* new_name_c = env->GetStringUTFChars(newName, nullptr);
    std::string new_path_str(old_path_c);
    size_t lastSlash = new_path_str.find_last_of("/\\");
    if (lastSlash != std::string::npos)
        new_path_str = new_path_str.substr(0, lastSlash + 1) + new_name_c;
    else
        new_path_str = new_name_c;
    bool success = (rename(old_path_c, new_path_str.c_str()) == 0);
    env->ReleaseStringUTFChars(oldPath, old_path_c);
    env->ReleaseStringUTFChars(newName, new_name_c);
    return success ? JNI_TRUE : JNI_FALSE;
}

bool deleteRecursive(const char* path) {
    struct stat status{};
    if (stat(path, &status) != 0) return false;
    if (S_ISDIR(status.st_mode)) {
        DIR* dir = opendir(path);
        if (!dir) return false;
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            std::string name(entry->d_name);
            if (name == "." || name == "..") continue;
            std::string fullPath = std::string(path) + "/" + name;
            if (!deleteRecursive(fullPath.c_str())) { closedir(dir); return false; }
        }
        closedir(dir);
        return (rmdir(path) == 0);
    } else {
        return (unlink(path) == 0);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lhacenmed_khatmah_feature_debug_NativeBindings_deleteFileOrDirectoryNative(
        JNIEnv* env, jobject, jstring path) {
    const char* path_c = env->GetStringUTFChars(path, nullptr);
    bool success = deleteRecursive(path_c);
    env->ReleaseStringUTFChars(path, path_c);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_lhacenmed_khatmah_feature_debug_NativeBindings_getFileMetadataNative(
        JNIEnv* env, jobject, jstring path) {
    const char* path_c = env->GetStringUTFChars(path, nullptr);
    if (!path_c) return nullptr;
    struct stat fileStat{};
    if (stat(path_c, &fileStat) != 0) { env->ReleaseStringUTFChars(path, path_c); return nullptr; }
    const char* lastSlash = strrchr(path_c, '/');
    const char* fileName  = lastSlash ? lastSlash + 1 : path_c;
    jclass fileMetadataClass = env->FindClass("com/lhacenmed/khatmah/feature/debug/FileMetadata");
    if (!fileMetadataClass) { env->ReleaseStringUTFChars(path, path_c); return nullptr; }
    jmethodID constructor = env->GetMethodID(fileMetadataClass, "<init>",
                                             "(Ljava/lang/String;Ljava/lang/String;JZJLjava/lang/String;JJJ)V");
    if (!constructor) { env->ReleaseStringUTFChars(path, path_c); return nullptr; }
    jstring nameStr  = env->NewStringUTF(fileName);
    jstring pathStr  = env->NewStringUTF(path_c);
    jstring permsStr = env->NewStringUTF(getPermissionsMode(fileStat.st_mode).c_str());
    jobject obj = env->NewObject(fileMetadataClass, constructor,
                                 nameStr, pathStr,
                                 (jlong)fileStat.st_size,
    (jboolean)S_ISDIR(fileStat.st_mode),
            (jlong)(fileStat.st_mtime * 1000),
            permsStr,
            (jlong)fileStat.st_ino,
            (jlong)fileStat.st_blksize,
            (jlong)fileStat.st_blocks);
    env->ReleaseStringUTFChars(path, path_c);
    env->DeleteLocalRef(nameStr);
    env->DeleteLocalRef(pathStr);
    env->DeleteLocalRef(permsStr);
    return obj;
}