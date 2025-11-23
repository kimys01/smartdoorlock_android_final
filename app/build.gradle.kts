plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.smartdoorlock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smartdoorlock"
        minSdk = 31 // UWB는 API 31(Android 12) 이상에서만 동작하므로 minSdk를 31로 올리는 것을 권장합니다. (아니면 코드에서 버전 체크 필요)
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Google Location Service
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // ✅ [필수 추가] UWB 라이브러리 (이게 없으면 UWB 기능 사용 불가)
    implementation("androidx.core.uwb:uwb:1.0.0-alpha08")

    // ✅ Firebase (BOM을 사용하여 버전 자동 관리)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // BOM을 쓰면 버전 숫자를 적지 않아도 됩니다. (중복 제거 및 정리)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx") // Realtime Database
    implementation("com.google.firebase:firebase-firestore-ktx") // Firestore (필요시 유지)

    // Retrofit2 (HTTP 통신용)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Kotlin Coroutines (비동기 처리)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // 버전을 최신으로 맞춤
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3") // Firebase와 코루틴 연동용

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}