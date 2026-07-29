package com.tinhcd.esalessfa.feature.splash

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.datastore.SessionManager
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ba đích đến khả dĩ khi mở app. */
enum class StartDestination { LOGIN, SYNC, HOME }

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            _destination.value = when {
                !authRepository.isLoggedIn() -> StartDestination.LOGIN
                // Đã đăng nhập nhưng chưa sync lần nào thì mọi màn hình khác đều
                // trống trơn, vì UI chỉ đọc từ Room.
                !sessionManager.hasCompletedFirstSync.first() -> StartDestination.SYNC
                else -> StartDestination.HOME
            }
        }
    }
}

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val viewModel: SplashViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            val destination = viewModel.destination.filterNotNull().first()
            val action = when (destination) {
                StartDestination.LOGIN -> R.id.action_splash_to_login
                StartDestination.SYNC -> R.id.action_splash_to_sync
                StartDestination.HOME -> R.id.action_splash_to_home
            }
            findNavController().navigate(action)
        }
    }
}
