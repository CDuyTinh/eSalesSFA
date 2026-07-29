package com.tinhcd.esalessfa.feature.auth

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLoginBinding.bind(view)

        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.loginButton.setOnClickListener { viewModel.signIn() }

        // repeatOnLifecycle: ngừng thu thập khi màn hình xuống nền, tránh giữ
        // tham chiếu tới view đã bị huỷ.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.loginButton.isEnabled = state.canSubmit
                        binding.progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        binding.errorText.text = state.errorMessage
                        binding.errorText.visibility =
                            if (state.errorMessage != null) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            LoginEvent.NavigateToSync ->
                                findNavController().navigate(R.id.action_login_to_sync)
                        }
                    }
                }
            }
        }
    }
}
