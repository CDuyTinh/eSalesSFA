package com.tinhcd.esalessfa.core.common

/**
 * Kết quả của một thao tác có thể thất bại.
 *
 * Dùng thay cho việc ném exception xuyên tầng: repository trả [AppResult],
 * ViewModel map sang UiState. Nhờ vậy lỗi mạng / lỗi nghiệp vụ / lỗi auth
 * được phân biệt rõ ràng thay vì gom hết vào một `catch (e: Exception)`.
 */
sealed interface AppResult<out T> {

    data class Success<T>(val data: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>

    data object Loading : AppResult<Nothing>
}

sealed interface AppError {

    /** Mất mạng, timeout, DNS... — có thể retry */
    data class Network(val cause: Throwable? = null) : AppError

    /** Token hết hạn / bị thu hồi — phải đăng nhập lại */
    data object Unauthorized : AppError

    /** Server từ chối vì quy tắc nghiệp vụ (hết quota KM, vượt hạn mức...) — KHÔNG retry */
    data class Business(val code: String, val message: String) : AppError

    /** Lỗi đọc/ghi DB local */
    data class Database(val cause: Throwable? = null) : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
    AppResult.Loading -> AppResult.Loading
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
