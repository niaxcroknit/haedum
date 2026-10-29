plugins {
    id("com.android.application")
}

android {
    namespace = "com.nia.haedum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nia.haedum"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // kotlin-stdlib 중복 클래스 오류 방지 (광고 SDK가 쓰는 Kotlin 2.1에 맞춤)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.1.0"))
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
    // 애드몹 광고
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    // 광고 SDK가 끌고 오는 옛 fragment 때문에 생기는 출시 검사 오류 방지 (알림 권한 요청에 1.3.0+ 필요)
    implementation("androidx.fragment:fragment:1.8.5")
}
