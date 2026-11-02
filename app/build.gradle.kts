import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Reads app/google-services.json (download it from your Firebase project)
    alias(libs.plugins.google.services)
}

// Secrets that must not be committed live in local.properties (it is git-ignored), for example:
//   AGORA_APP_ID=your-agora-app-id
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.kinnect.i221329_i221069"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kinnect.i221329_i221069"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Agora project id used for voice and video calls (read as BuildConfig.AGORA_APP_ID)
        buildConfigField("String", "AGORA_APP_ID", "\"" + localProperties.getProperty("AGORA_APP_ID", "") + "\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Backend: Firebase Authentication, Realtime Database and Cloud Messaging (push notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    // Voice and video calls
    implementation(libs.agora.rtc)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}