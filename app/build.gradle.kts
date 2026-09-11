plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shuddhatype"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shuddhatype"
        minSdk = 24          // Android 7.0 — covers the phones actually used in Nepal
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
    }

    androidResources {
        // words.txt.gz and verb_roots.txt.gz are already compressed; letting
        // aapt re-compress them wastes build time and gains nothing.
        noCompress += listOf("gz")
    }

    // Signing comes from the environment, never from a file in the repo. On a
    // laptop none of these are set and the block stays empty, so a debug build
    // still works with nothing configured; on the release workflow all four
    // arrive from GitHub secrets. A keystore committed to a public repo is an
    // app somebody else can publish updates to.
    signingConfigs {
        create("release") {
            val store = System.getenv("KEYSTORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only when the key is actually there. Without this guard a local
            // release build fails with an unhelpful error about a null keystore.
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
