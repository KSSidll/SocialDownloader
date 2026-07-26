plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kssidll.socialdownloader"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kssidll.socialdownloader"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")

            optimization {
                enable = true
                isMinifyEnabled = true
                isShrinkResources = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}