plugins {
    alias(libs.plugins.android.application)
    // Reads app/google-services.json (download it from your Firebase project)
    alias(libs.plugins.google.services)
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

    // Backend: Firebase Authentication + Cloud Firestore
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Voice and video calls
    implementation(libs.stream.webrtc)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}