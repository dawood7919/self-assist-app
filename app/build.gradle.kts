plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dawood.orbit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dawood.orbit"
        minSdk = 26
        targetSdk = 35
        // Rises with every CI build so a new APK installs over the old one as
        // an upgrade. A fixed versionCode makes Android treat every build as
        // the same version, which is the other half of the "uninstall the old
        // app first" problem.
        versionCode = (System.getenv("ORBIT_VERSION_CODE") ?: "1").toInt()
        versionName = "0.2.${System.getenv("ORBIT_VERSION_CODE") ?: "0"}"
    }

    signingConfigs {
        create("ci") {
            // Both build types share one stable key, so an APK from any build
            // installs over any other without uninstalling first. Android
            // refuses to replace an app with one signed by a different key,
            // and CI's auto-generated debug key is different every run — which
            // is why every install used to demand a wipe, taking the app's
            // data with it.
            storeFile = file("orbit-ci.jks")
            storePassword = "orbitci"
            keyAlias = "orbit"
            keyPassword = "orbitci"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("ci")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // PDFBox brings its own notices and service files, several of which
            // collide with the ones already on the packaging path.
            excludes += "/META-INF/{DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.navigation.compose)

    // Video downloader: range-request downloads and page parsing.
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // PDF merge, split, watermark and image re-encoding. Chosen over rendering
    // pages to bitmaps because it keeps text selectable in the output.
    implementation(libs.pdfbox.android)

    // QR and barcode encoding, and decoding a code out of a photo. The core
    // library is pure Java, so reading a code needs no camera permission.
    implementation(libs.zxing.core)

    // Offline text recognition. The bundled model is chosen over the Play
    // Services one so the tool works on a device without Google services,
    // at the cost of a larger APK.
    implementation(libs.mlkit.text.recognition)

    // Playing a video while it is still downloading, and previewing a link
    // before committing to the download.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // The calculators and generators are pure logic, so they are covered by
    // JVM unit tests that run in CI without a device.
    testImplementation(libs.junit)
    // The Android JVM test runtime stubs org.json, so the codecs need the real
    // implementation on the unit-test classpath.
    testImplementation(libs.json)
}
