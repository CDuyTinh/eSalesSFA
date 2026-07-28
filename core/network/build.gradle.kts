import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tinhcd.esalessfa.core.network"
    compileSdk { version = release(libs.versions.compileSdk.get().toInt()) }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        // Đọc từ local.properties -> KHÔNG commit key lên git
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${props.getProperty("SUPABASE_URL") ?: System.getenv("SUPABASE_URL") ?: ""}\""
        )
        // Publishable key (sb_publishable_...), KHÔNG phải legacy anon key.
        // Key này an toàn khi nằm trong APK — bảo mật thật sự đến từ RLS policy trên Postgres.
        buildConfigField(
            "String", "SUPABASE_PUBLISHABLE_KEY",
            "\"${props.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\""
        )
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:common"))

    // Supabase: CHỈ Auth (đăng nhập, quản lý JWT) và Storage (upload ảnh trực tiếp).
    // Cố ý KHÔNG khai báo postgrest-kt — dữ liệu nghiệp vụ phải đi qua Edge Functions,
    // thiếu dependency này thì lỡ tay gọi thẳng bảng cũng không compile được.
    api(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    // REST client gọi Edge Functions
    api(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.unit.test)
}
