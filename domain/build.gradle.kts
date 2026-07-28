import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :domain — PURE KOTLIN JVM.
// KHÔNG được thêm bất kỳ dependency nào của Android vào module này.
// Đây là ràng buộc do compiler enforce: business logic không thể lỡ tay import android.*
// => test chạy trên JVM (nhanh, không cần emulator), và đổi UI XML -> Compose không đụng tới đây.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    // Phải khớp với java targetCompatibility ở trên, và khớp với các module Android
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation(libs.bundles.unit.test)
}

tasks.withType<Test> {
    useJUnit()
}
