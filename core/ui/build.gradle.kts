plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tinhcd.esalessfa.core.ui"
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
    implementation(project(":core:common"))

    api(libs.bundles.androidx.ui)
    api(libs.bundles.lifecycle)
    api(libs.bundles.navigation)
    api(libs.coil)
    api(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.bundles.unit.test)
}
