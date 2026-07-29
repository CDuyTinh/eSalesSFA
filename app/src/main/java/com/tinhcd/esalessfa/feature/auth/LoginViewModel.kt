package com.tinhcd.esalessfa.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.core.common.AppError
import com.tinhcd.esalessfa.core.common.AppResult
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

/** Sự kiện một lần — không đưa vào state để tránh phát lại khi xoay máy. */
sealed interface LoginEvent {
    data object NavigateToSync : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onEmailChanged(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChanged(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun signIn() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authRepository.signIn(state.email, state.password)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.NavigateToSync)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toMessage())
                }

                AppResult.Loading -> Unit
            }
        }
    }

    private fun AppError.toMessage(): String = when (this) {
        is AppError.Network -> "Không có kết nối mạng. Kiểm tra lại rồi thử tiếp."
        AppError.Unauthorized -> "Phiên đăng nhập đã hết hạn."
        is AppError.Business -> message
        is AppError.Database -> "Lỗi dữ liệu cục bộ."
        is AppError.Unknown -> "Đã xảy ra lỗi không xác định."
    }
}
