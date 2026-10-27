plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.gateau.tvhome"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.gateau.tvhome"
        minSdk = 24
        targetSdk = 30
        versionCode = 3
        versionName = "2.1"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("tvhome.keystore")
            storePassword = "tvhome123"
            keyAlias = "tvhome"
            keyPassword = "tvhome123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
