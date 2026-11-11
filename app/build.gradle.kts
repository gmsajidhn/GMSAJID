plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.sajid.studytracker"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.sajid.studytracker"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "72.54.3"
    }
    // সবসময় একই সাইন-কী ব্যবহার হয়, তাই নতুন ভার্সন পুরোনোটার উপর আপডেট হয় (আনইনস্টল লাগে না)
    signingConfigs {
        create("fixed") {
            storeFile = file("sajid.keystore")
            storePassword = "sajid2026"
            keyAlias = "sajid"
            keyPassword = "sajid2026"
            storeType = "pkcs12"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
