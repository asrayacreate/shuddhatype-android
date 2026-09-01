plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shuddhatype"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shuddhatype"
        minSdk = 24          // Android 7.0 — covers the phones actually used in Nepal
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    androidResources {
        // words.txt.gz and verb_roots.txt.gz are already compressed; letting
        // aapt re-compress them wastes build time and gains nothing.
        noCompress += listOf("gz")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // No AppCompat, no core-ktx. Not a single class from either was used, and
    // appcompat drags in emoji2 -> androidx.startup, which installs a
    // ContentProvider that runs before Application.onCreate(). An unused
    // dependency that can fail before your code starts is pure downside.
    testImplementation("junit:junit:4.13.2")
}
