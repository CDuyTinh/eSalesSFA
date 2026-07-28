plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tinhcd.esalessfa.feature.auth"
    compileSdk { version = release(libs.versions.compileSdk.get().toInt()) }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // ⚠️ LUẬT KIẾN TRÚC: feature KHÔNG được phụ thuộc :data.
    // Chỉ nói chuyện với domain qua interface -> đổi XML sang Compose không ảnh hưởng tầng dưới.
    implementation(project(":domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.unit.test)
}
