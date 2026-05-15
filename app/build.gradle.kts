plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.scrollshield"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scrollshield"
        minSdk = 28
        targetSdk = 36
        versionCode = 21
        versionName = "0.18.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "2g"
        }
    }

    sourceSets {
        getByName("test") {
            resources.srcDirs("src/test/resources")
        }
        getByName("androidTest") {
            assets.srcDirs("src/androidTest/assets")
        }
    }

}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.dagger:hilt-android:2.55")
    ksp("com.google.dagger:hilt-android-compiler:2.55")

    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("com.google.mlkit:text-recognition:16.0.0")

    implementation("com.github.adaptech-cz:Tesseract4Android:4.7.0") {
        exclude(group = "com.github.adaptech-cz.Tesseract4Android", module = "tesseract4android-openmp")
    }

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("io.mockk:mockk-android:1.13.10")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20231013")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.10")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// ---- WI-15: perf gate ----
// Reads ${buildDir}/perf/perf-report.json (produced by perf unit tests) and
// fails the build if medianClassificationLatencyMs exceeds 120 ms.
tasks.register("perfGateCheck") {
    description = "Validates classification latency from perf-report.json"
    group = "verification"
    dependsOn("testDebugUnitTest")
    doLast {
        val reportFile = file("${layout.buildDirectory.get().asFile}/perf/perf-report.json")
        if (!reportFile.exists()) {
            throw GradleException("Perf report not found at ${reportFile.absolutePath}. Did perf tests run?")
        }
        val text = reportFile.readText()
        val regex = Regex("\"medianClassificationLatencyMs\"\\s*:\\s*([0-9.]+)")
        val match = regex.find(text)
            ?: throw GradleException("medianClassificationLatencyMs missing in perf report")
        val median = match.groupValues[1].toDouble()
        if (median > 120.0) {
            throw GradleException(
                "FAIL: classification latency gate — medianClassificationLatencyMs=$median exceeds 120 ms"
            )
        }
        println("[perfGateCheck] OK medianClassificationLatencyMs=$median")
    }
}
