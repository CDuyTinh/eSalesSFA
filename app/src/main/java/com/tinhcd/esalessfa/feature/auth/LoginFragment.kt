package com.tinhcd.esalessfa.feature.auth

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.BuildConfig
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLoginBinding.bind(view)

        view.padTopForStatusBar()

        binding.versionBuildText.text = getString(R.string.login_version_build, buildStamp())
        binding.versionProductText.text =
            getString(R.string.login_version_product, BuildConfig.VERSION_NAME)

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

    /**
     * Mốc thời gian bản đang chạy, hiển thị ở chân màn hình như bản gốc.
     *
     * Lấy thời điểm cài/cập nhật app thay vì nhét thời điểm build qua
     * `buildConfigField`: giá trị đổi mỗi lần build sẽ làm hỏng configuration
     * cache của Gradle và buộc biên dịch lại BuildConfig sau mọi lần sửa code.
     */
    private fun buildStamp(): String {
        val pm = requireContext().packageManager
        val name = requireContext().packageName
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, 0)
        }
        return SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(info.lastUpdateTime))
    }
}
