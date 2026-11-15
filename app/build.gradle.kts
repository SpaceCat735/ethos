import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 서명 정보는 local.properties(gitignore)에서 읽는다. 없으면 서명 없이 빌드만 된다 (CI·다른 PC에서도 깨지지 않게).
//   release.storeFile=/절대/경로/naechamon-release.jks
//   release.storePassword=...
//   release.keyAlias=naechamon
//   release.keyPassword=...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("release.storeFile")?.let { file(it) }

android {
    namespace = "com.naechamon"
    compileSdk = 35
    // AAOS 차속·주행 제한(CarPropertyManager, CarUxRestrictionsManager). 컴파일용, 기기에 이미 있는 라이브러리를 쓴다
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.naechamon"
        // Pleos 앱 마켓 배포 조건: API 28 이상 (서명 키 교체 지원)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null && releaseStoreFile.exists()) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 데모 제출용: 난독화·축소 없음 (심사 시 APK를 그대로 설치해 본다)
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.pleos.vehicle)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
