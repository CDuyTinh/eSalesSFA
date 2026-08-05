import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tinhcd.esalessfa"
    compileSdk { version = release(libs.versions.compileSdk.get().toInt()) }

    defaultConfig {
        applicationId = "com.tinhcd.esalessfa"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Đọc từ local.properties -> KHÔNG commit key lên git.
        // `java` bị Gradle shadow nên phải import java.util.Properties ở đầu file.
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${props.getProperty("SUPABASE_URL") ?: System.getenv("SUPABASE_URL") ?: ""}\""
        )
        // Publishable key (sb_publishable_...), KHÔNG phải legacy anon key.
        // Key này an toàn khi nằm trong APK — bảo mật thật sự đến từ RLS + Edge Function.
        buildConfigField(
            "String", "SUPABASE_PUBLISHABLE_KEY",
            "\"${props.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\""
        )

        // Khoá Google Maps đi vào manifest qua placeholder. Thiếu khoá thì SDK chỉ
        // vẽ ra một ô xám câm lặng, nên đưa thêm cờ vào BuildConfig để màn bản đồ
        // nói thẳng "chưa cấu hình khoá" thay vì để người dùng ngồi đoán.
        val mapsKey = props.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("boolean", "HAS_MAPS_KEY", "${mapsKey.isNotBlank()}")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Xuất schema Room ra JSON -> commit vào git để review migration trong PR
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ── UI ────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.bundles.androidx.ui)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.navigation)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    // ── DI ────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Local ─────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // ── Async / Background ────────────────────────────────────────────────
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)

    // ── Vị trí &amp; bản đồ ───────────────────────────────────────────────────
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    // ── Camera ────────────────────────────────────────────────────────────
    implementation(libs.bundles.camerax)
    implementation(libs.androidx.exifinterface)

    // ── Network ───────────────────────────────────────────────────────────
    // Supabase: CHỈ Auth (JWT) và Storage (ảnh). Cố ý KHÔNG khai báo postgrest-kt —
    // dữ liệu nghiệp vụ phải đi qua Edge Functions, thiếu dependency này thì lỡ tay
    // gọi thẳng bảng cũng không compile được.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // ── Test ──────────────────────────────────────────────────────────────
    testImplementation(libs.bundles.unit.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
