plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.uroif.notificationforwarder"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.uroif.notificationforwarder"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.260115"
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
    kotlinOptions {
        jvmTarget = "11"
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.1")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.0.3")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.0.3")
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-utils:2.3.7")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.media:media:1.7.0")
}
