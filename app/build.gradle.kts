// CallQTV app module – Android TV / IoT token display. AGP 8.7.2 + Kotlin 1.9.x
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
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
            // Explicitly mark as debuggable for App Inspection/Layout Inspector
            isDebuggable = true
            // Ensure no minification in debug
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("config")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
        dataBinding = false
        viewBinding = false
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

base.archivesName.set("CallQTV_v${android.defaultConfig.versionName}")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")

    // Jetpack Compose (BOM 2024.12.01)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // Tooling dependencies must be 'debugImplementation' for App Inspection/Layout Inspector
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    // Kotlin + Jetpack testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("com.airbnb.android:lottie:6.6.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Retrofit & Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Jetpack Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // QR Code (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Room - Upgraded to 2.7.0-alpha12 for Kotlin 2.1.0 support
    val roomVersion = "2.7.0-alpha12"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.core:core-ktx:1.15.0")

    // ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.0")

    // MQTT
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // AI ML Kit
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta5")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
