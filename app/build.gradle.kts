plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

android {
    namespace = "vn.futaland.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.futaland.realestate"
        minSdk = 26
        targetSdk = 36
        versionCode = 47
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("/Users/tafi/Downloads/upload-keystore.jks").takeIf { it.exists() } ?: file("upload-keystore.jks")
            storePassword = "futaland789"
            keyAlias = "upload"
            keyPassword = "futaland789"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Local JVM unit tests cover only the shared pure-Kotlin product policy code.
// Compile them for the JDK that actually runs the test worker: CI ships JDK 21,
// while a local machine may only have 17 (a class file version 65 cannot be
// loaded by a 17 test worker).
// The app targets Java 21. Gradle runs test workers on its own JVM, so a machine
// whose JAVA_HOME is still 17 (with Homebrew's keg-only JDK 21 installed beside it)
// cannot load the app classes. Point the unit-test worker at a local JDK 21 when we
// can find one; CI already runs on JDK 21 and keeps the default launcher.
val unitTestJava21 = listOf(
    "/opt/homebrew/opt/openjdk@21/bin/java",
    "/usr/local/opt/openjdk@21/bin/java",
).map { File(it) }.firstOrNull { it.isFile }
if (unitTestJava21 != null) {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        executable = unitTestJava21.absolutePath
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Firebase Cloud Messaging (push notifications)
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")

    // Google Play In-App Updates
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coil 3 for Image Loading & Cache
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Security & Encrypted Preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Local JVM unit tests (product access & navigation policy regression)
    testImplementation("junit:junit:4.13.2")

    // Debugging
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
