plugins {
    id("com.android.application")
}

android {
    namespace = "com.sirius.unlockbrightness"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sirius.unlockbrightness"
        minSdk = 29
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // LibXposed API 由框架在运行时提供，绝不能打包进 APK，因此必须是 compileOnly。
    compileOnly("io.github.libxposed:api:102.0.0")
}
