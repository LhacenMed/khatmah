@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

sealed class Version(
    open val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val versionBuild: Int = 0,
) {
    abstract fun toVersionName(): String

    class Alpha(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch-alpha.$versionBuild"
    }

    class Beta(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch-beta.$versionBuild"
    }

    class Stable(
        versionMajor: Int, versionMinor: Int, versionPatch: Int,
    ) : Version(versionMajor, versionMinor, versionPatch) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch"
    }

    class ReleaseCandidate(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch-rc.$versionBuild"
    }
}

val currentVersion: Version = Version.Stable(
    versionMajor = 1,
    versionMinor = 0,
    versionPatch = 9,
)

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

// Release builds always emit per-ABI APKs + a universal one (store/sideload distribution);
// debug stays a single fast universal APK. Pass -Psplits to force splits for any build type.
val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val splitApks = project.hasProperty("splits") || isReleaseBuild

val abiFilterList = (properties["ABI_FILTERS"] as? String)
    ?.split(';')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf("arm64-v8a")

android {
    namespace  = "com.lhacenmed.khatmah"
    compileSdk = 36

    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties().apply {
            load(FileInputStream(keystorePropertiesFile))
        }
        signingConfigs {
            create("releaseKey") {
                keyAlias      = keystoreProperties["keyAlias"].toString()
                keyPassword   = keystoreProperties["keyPassword"].toString()
                storeFile     = file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"].toString()
            }
        }
    }

    defaultConfig {
        applicationId = "com.lhacenmed.khatmah"
        minSdk        = 24
        targetSdk     = 36
        versionCode   = currentVersion.run { versionMajor * 10000 + versionMinor * 100 + versionPatch }
        versionName   = currentVersion.toVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ABI splits are opt-in (-Psplits). Without the flag every build produces a
    // single APK filtered to abiFilterList, which can be sideloaded directly.
    if (splitApks) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled      = false
            isShrinkResources    = false
            applicationIdSuffix  = ".debug"
            versionNameSuffix    = "-debug"
            resValue("string", "app_name", "Khatmah Debug")
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("releaseKey")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("releaseKey")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose     = true
        buildConfig = true
        viewBinding = true
        resValues   = true  // required for resValue() in build types (AGP 8+)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

androidComponents {
    onVariants { variant ->
        val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            // Per-ABI APKs get a distinct versionCode so each ABI is independently updatable.
            // The single non-split APK keeps the base code (no bump).
            if (splitApks) {
                abiCodes[abi ?: abiFilterList.firstOrNull()]?.let { code ->
                    output.versionCode.set(code + (output.versionCode.get() ?: 0))
                }
            }
            // Name every artifact "khatmah-<version>-<abi>.apk" ("…-universal.apk" for the
            // ABI-less output) so a bare "app-release.apk" can never be produced.
            (output as? VariantOutputImpl)?.outputFileName
                ?.set("khatmah-${currentVersion.toVersionName()}-${abi ?: "universal"}.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidsvg)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.org.brotli.dec)
    implementation(libs.commons.compress)
    implementation(libs.xz)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}