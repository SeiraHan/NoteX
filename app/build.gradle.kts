plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.notex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.notex"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
