import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * 업로드 키 정보. 저장소에는 두지 않습니다(keystore.properties 는 .gitignore 대상,
 * 키스토어 파일 자체는 저장소 밖에 둡니다). 형식은 keystore.properties.example 참고.
 *
 * 파일이 없으면 release 도 debug 키로 서명합니다 — 클론해서 그냥 빌드해 보려는
 * 사람이 키를 만들지 않고도 돌려볼 수 있어야 하기 때문입니다.
 * 배포용 빌드인지는 아래 hasUploadKey 로 갈립니다.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasUploadKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.hj0128.todolock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hj0128.todolock"
        minSdk = 24
        targetSdk = 36
        // versionCode 는 시스템이 보는 번호라 절대 낮추지 않습니다(낮추면 업데이트가
        // 깔리지 않습니다). versionName 은 사람이 보는 이름이라 자유롭게 정합니다.
        // 1.4 까지는 공개하지 않고 내 컴퓨터에서만 돌던 번호라, 처음 배포하는
        // 이번 것을 1.0 으로 둡니다.
        versionCode = 8
        versionName = "1.0.2"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 배포하는 APK 는 반드시 업로드 키로 서명해야 합니다.
            // debug 키는 비밀번호가 공개된 표준 키라, 그걸로 서명해 배포하면
            // 누구나 같은 서명의 '업데이트' 를 만들어 덮어씌울 수 있습니다.
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
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
        viewBinding = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
