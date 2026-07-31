package com.tinhcd.esalessfa.feature.survey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.UUID

/**
 * Chụp ảnh minh chứng bằng CameraX.
 *
 * Dùng CameraX thay vì Intent gọi app camera hệ thống: app ngoài trả về ảnh với
 * kích thước và định dạng không kiểm soát được, và trên nhiều máy Trung Quốc nó
 * còn tự thêm watermark riêng đè lên dấu của mình.
 */
@AndroidEntryPoint
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private var imageCapture: ImageCapture? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showPermissionDenied()
    }

    /**
     * Từ chối kèm "Không hỏi lại" thì hệ thống bỏ qua mọi lần xin sau, dialog
     * không bao giờ hiện nữa. Đường duy nhất còn lại là mở Cài đặt ứng dụng.
     */
    private fun showPermissionDenied() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_denied)
            .setPositiveButton(R.string.checkin_open_settings) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().packageName, null),
                    )
                )
                findNavController().navigateUp()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> findNavController().navigateUp() }
            .setCancelable(false)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCameraBinding.bind(view)

        // Chừa chỗ ở nút Đóng, không ở view gốc: khung ngắm phải chiếm trọn màn
        // hình, đệm view gốc sẽ bóp nó lại thành một dải trống ở đỉnh.
        binding.closeButton.padTopForStatusBar()

        binding.closeButton.setOnClickListener { findNavController().navigateUp() }
        binding.shutterButton.setOnClickListener { capture() }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val binding = FragmentCameraBinding.bind(requireView())
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())

        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            // MINIMIZE_LATENCY: nhân viên chụp liên tiếp nhiều tấm trong cửa
            // hàng, chờ tối ưu chất lượng từng tấm sẽ rất khó chịu. Ảnh dù sao
            // cũng bị nén xuống 300KB ngay sau đó.
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }.onFailure {
                Snackbar.make(requireView(), R.string.camera_start_failed, Snackbar.LENGTH_LONG)
                    .show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capture() {
        val capture = imageCapture ?: return

        // Ảnh thô ghi vào cache: nó chỉ sống tới khi ImageProcessor nén xong rồi
        // bị xoá, không cần chiếm bộ nhớ chính của máy.
        val file = File(
            File(requireContext().cacheDir, "camera").apply { mkdirs() },
            "${UUID.randomUUID()}.jpg",
        )

        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    findNavController().previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(RESULT_PHOTO_PATH, file.absolutePath)
                    findNavController().navigateUp()
                }

                override fun onError(exception: ImageCaptureException) {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.camera_capture_failed, exception.message.orEmpty()),
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    companion object {
        const val RESULT_PHOTO_PATH = "photo_path"

        fun args(questionId: String) = bundleOf("questionId" to questionId)
    }
}
