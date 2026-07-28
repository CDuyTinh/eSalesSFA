package com.tinhcd.esalessfa.core.network.di

import com.tinhcd.esalessfa.core.network.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import javax.inject.Singleton

/**
 * Supabase ở đây chỉ đảm nhiệm hai việc:
 *
 *  1. [Auth]    — đăng nhập, lưu & tự refresh JWT.
 *  2. [Storage] — upload ảnh minh chứng trực tiếp lên bucket.
 *                 Ảnh đi thẳng lên Storage thay vì qua Edge Function, vì đẩy file
 *                 nhị phân qua function vừa chậm vừa chạm giới hạn payload.
 *
 * Toàn bộ dữ liệu nghiệp vụ (sync, đơn hàng, khuyến mãi) KHÔNG dùng Postgrest mà
 * gọi Edge Functions qua Retrofit — xem [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL rỗng — thêm vào local.properties (xem local.properties.example)"
        }
        check(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "SUPABASE_PUBLISHABLE_KEY rỗng — thêm vào local.properties"
        }

        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Storage)
        }
    }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient): Auth = client.auth

    @Provides
    @Singleton
    fun provideStorage(client: SupabaseClient): Storage = client.storage
}
