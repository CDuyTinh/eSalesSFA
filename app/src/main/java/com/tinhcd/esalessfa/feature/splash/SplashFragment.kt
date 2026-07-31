package com.tinhcd.esalessfa.feature.splash

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentSplashBinding
import com.tinhcd.esalessfa.domain.usecase.StartDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val viewModel: SplashViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSplashBinding.bind(view)

        val zoomIn = AnimationUtils.loadAnimation(requireContext(), R.anim.splash_zoom_in)
        binding.logoGroup.startAnimation(zoomIn)
        binding.footerText.startAnimation(zoomIn)

        viewLifecycleOwner.lifecycleScope.launch {
            // Chạy song song với đồng hồ chờ, không nối tiếp: kiểm tra phiên chỉ
            // đọc DataStore nên xong trong vài chục ms. Điều hướng ngay thì logo
            // chỉ nhá qua một khung hình và hoạt ảnh không kịp thấy.
            val destination = async { viewModel.destination.filterNotNull().first() }
            delay(MIN_VISIBLE_MS)

            val action = when (destination.await()) {
                StartDestination.LOGIN -> R.id.action_splash_to_login
                StartDestination.SYNC -> R.id.action_splash_to_sync
                StartDestination.HOME -> R.id.action_splash_to_home
            }
            findNavController().navigate(action)
        }
    }

    private companion object {
        const val MIN_VISIBLE_MS = 900L
    }
}
