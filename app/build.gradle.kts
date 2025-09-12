plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ussoi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ussoi"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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

    implementation("com.github.prasad-psp:Android-Bluetooth-Library:1.0.2")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}