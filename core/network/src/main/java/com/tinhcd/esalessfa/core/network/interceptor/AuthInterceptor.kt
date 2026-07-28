package com.tinhcd.esalessfa.core.network.interceptor

import com.tinhcd.esalessfa.core.network.BuildConfig
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gắn `apikey` và `Authorization: Bearer <jwt>` vào mọi request tới Edge Functions.
 *
 * Edge Function của Supabase mặc định tự verify JWT trước khi chạy code, nên thiếu
 * header này là bị chặn ngay ở cổng với 401 — hàm không hề được gọi.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val auth: Auth,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp interceptor là API đồng bộ; đọc token trong runBlocking là chấp nhận
        // được vì lời gọi này vốn đã nằm trên thread nền của OkHttp.
        val token = runBlocking { auth.currentAccessTokenOrNull() }

        val request = chain.request().newBuilder()
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        return chain.proceed(request)
    }
}
