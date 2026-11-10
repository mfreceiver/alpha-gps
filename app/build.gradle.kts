import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    id("androidx.room")
    id("com.google.devtools.ksp")
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.saschl.cameragps"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    // Reproducible builds: native libs (libsqliteJni.so from sqlite-bundled) are
    // stripped with the NDK's llvm-strip, so CI and F-Droid must use the SAME
    // NDK. Keep in sync with `ndk:` in the fdroiddata
    ndkVersion = "29.0.14206865"

    androidResources {
        generateLocaleConfig = true
        // Ship only the English baseline + Simplified Chinese; keeps library
        // locales (Material etc.) out of the APK too. Sync res dirs & AGENTS.md
        // when changing this.
        localeFilters.addAll(listOf("en", "zh-rCN"))
    }

    bundle {
        language {
            enableSplit = false
        }
    }


    defaultConfig {
        // Custom build: distinct applicationId so it installs alongside the
        // original com.saschl.cameragps. Namespace stays com.saschl.cameragps
        // so Kotlin packages / R class are untouched.
        applicationId = "cn.vectory.alphagps"
        minSdk = 31
        targetSdk = 37
        versionCode = providers.gradleProperty("alphagps.versionCode").get().toInt()
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64-only（用户要求）：只打包/构建 arm64-v8a，缩短 native 构建时间。
        // 老 32 位（armeabi-v7a）与 x86/x86_64 设备/模拟器不可安装本应用。
        // heif 子模块的 externalNativeBuild 同步设置，CMake 只编译 arm64-v8a。
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // F-Droid builds from source without the keystore or signing secrets — release
    // must fall back to an unsigned APK instead of failing.
    val releaseKeystore = file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "keystore.jks")
    val releaseSigningAvailable =
        releaseKeystore.exists() && System.getenv("SIGNING_KEY_ALIAS") != null

    signingConfigs {
        create("release") {
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            storeFile = releaseKeystore
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                if (releaseSigningAvailable) signingConfigs.getByName("release") else null

        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Play Store build: GMS fused location, Play in-app review.
        create("gplay") {
            dimension = "distribution"
            isDefault = true
        }
        // F-Droid build: no GMS, no Play libraries.
        create("foss") {
            dimension = "distribution"
        }
    }

    dependenciesInfo {
        // The dependency-info block is an encrypted blob only Google Play can read;
        // F-Droid rejects APKs that contain it.
        includeInApk = false
        includeInBundle = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }

}

dependencies {

    implementation(project(":sharednew"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.service)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.timber)
    implementation(libs.logging)
    //implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.accompanist.permissions)

    // Proprietary bits stay out of the foss (F-Droid) flavor.
    "gplayImplementation"(libs.google.play.services.location)
    "gplayImplementation"(libs.review)
    "gplayImplementation"(libs.review.ktx)

    implementation(libs.material)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.lifecycle.process)

    implementation(libs.androidx.compose.runtime.livedata)
    //implementation(libs.betterypermissionhelper)

    // Room database dependencies
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    //implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.appcompat)

    implementation(libs.components.resources)

}

