plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.yunkong.monitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yunkong.monitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            val path = providers.gradleProperty("releaseKeystore").orNull
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = providers.environmentVariable("YUNKONG_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.environmentVariable("YUNKONG_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
