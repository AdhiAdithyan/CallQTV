// CallQTV app module – Android TV / IoT token display. AGP 8.7, Kotlin 1.9 + kapt (Room).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.softland.callqtv"
    compileSdk = 35

    signingConfigs {
        create("config") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("F:\\I Drive\\Adithyan\\Android_keystore\\limar.keystore")
            storePassword = "android"
        }
    }

    val isLiveApk = true
    var palmAnd = ""

    defaultConfig {
        applicationId = "com.softland.callqtv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        palmAnd = if (isLiveApk) "LIVE_v" else "QA_v"
        buildConfigField("String", "BUILD_TYPE_DEVICE", "\"android_tv\"")
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val sep = "_"
                val variantName = variant.name
                val version = variant.versionName
                output.outputFileName = "${variantName}${sep}${palmAnd}$version.apk"
            }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("config")
        }
        debug {
            signingConfig = signingConfigs.getByName("config")
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
        buildConfig = true
        compose = true
        resValues = true  // Required for product flavor resValue() with AGP 9 + newDsl=false
        // No XML layouts: UI is 100% Jetpack Compose
        dataBinding = false
        viewBinding = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
            kotlin.srcDirs("src/main/kotlin")
            aidl {
                srcDirs("src/main/aidl")
            }
        }
    }

    flavorDimensions += "environment"

    productFlavors {
        create("CallQTV") {
            dimension = "environment"
            applicationId = "com.softland.callqtv"
            resValue("string", "app_name", "CallQTV")
            resValue("string", "app_icon_name", "app_icon_new")
            manifestPlaceholders["appIcon"] = "@drawable/callq_tv_logo"
            manifestPlaceholders["appIconRound"] = "@drawable/callq_tv_logo"
            manifestPlaceholders["appName"] = "CallQTV"
        }
    }
}

// Gradle 9.0 compatible: use archivesName instead of deprecated archivesBaseName (BasePluginExtension)
base.archivesName.set("CallQTV_v${android.defaultConfig.versionName}")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")

    // Jetpack Compose (BOM 2024.08 – stable for IoT/TV)
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    // Kotlin + Jetpack testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.25")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("com.airbnb.android:lottie:6.5.1")
    implementation("androidx.cardview:cardview:1.0.0")

    // Retrofit & Gson (IoT API calls)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Jetpack Lifecycle (ViewModel, LiveData, runtime KTX)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Coroutines (IoT/MQTT)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // QR Code (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Room (Jetpack – IoT config cache); kapt matches Kotlin 1.9.25
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.core:core-ktx:1.15.0")

    // ExoPlayer (audio playback)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // MQTT (Paho – IoT token updates)
    // Using pure Java client to avoid Android Service/PendingIntent issues on API 31+
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Coil (ad image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // AI ML Kit (Entity Extraction for NLP parsing - works on-device)
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta5")
}
