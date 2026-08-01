package com.tinhcd.esalessfa.feature.visit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentCheckInBinding
import com.tinhcd.esalessfa.domain.visit.CheckInValidation
import com.tinhcd.esalessfa.feature.survey.CameraFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class CheckInFragment : Fragment(R.layout.fragment_check_in) {

    private val viewModel: CheckInViewModel by viewModels()
    private var selectedReason: String? = null

    /**
     * Android 13+ tách quyền thông báo ra riêng, và Foreground Service loại
     * location bắt buộc phải hiện notification — thiếu quyền này thì service
     * bị chặn ngay khi khởi động.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.startLocation()
        } else {
            showPermissionDenied()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCheckInBinding.bind(view)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        view.padTopForStatusBar()

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.reasonInput.setOnItemClickListener { parent, _, position, _ ->
            selectedReason = viewModel.uiState.value.reasonCodes.getOrNull(position)?.code
        }

        binding.refreshButton.setOnClickListener { viewModel.refreshLocation() }

        val openCamera = View.OnClickListener {
            findNavController().navigate(R.id.action_checkIn_to_camera)
        }
        binding.photoBox.setOnClickListener(openCamera)
        binding.retakeButton.setOnClickListener(openCamera)
        observeCameraResult()

        binding.actionButton.setOnClickListener {
            val state = viewModel.uiState.value
            val note = binding.noteInput.text?.toString()

            if (state.isCheckedIn) {
                viewModel.checkOut(note = note)
            } else {
                if (state.needsReason && selectedReason == null) {
                    Snackbar.make(view, R.string.checkin_reason_required, Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (state.needsPhoto && state.photo == null) {
                    Snackbar.make(view, R.string.checkin_photo_required, Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.checkIn(selectedReason, note, batteryPercent())
            }
        }

        requestPermissions()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.customerName.text = state.customer?.name.orEmpty()
                        binding.customerAddress.text = state.customer?.address.orEmpty()

                        binding.locationProgress.isVisible = state.isWaitingLocation
                        // Chỉ khoá nút khi ĐANG đo: đo xong mà vẫn khoá thì người
                        // dùng không còn cách nào đo lại.
                        binding.refreshButton.isEnabled = !state.isWaitingLocation

                        binding.accuracyText.text = state.sample?.let {
                            getString(R.string.checkin_meters, it.accuracy.roundToInt())
                        } ?: getString(R.string.value_empty)

                        renderValidation(binding, state.validation)

                        bindPhoto(binding, state)

                        binding.reasonLayout.isVisible = state.needsReason
                        if (state.reasonCodes.isNotEmpty()) {
                            binding.reasonInput.setAdapter(
                                ArrayAdapter(
                                    requireContext(),
                                    android.R.layout.simple_list_item_1,
                                    state.reasonCodes.map { it.name },
                                )
                            )
                        }

                        binding.visitInfo.isVisible = state.isCheckedIn
                        state.openVisit?.let { visit ->
                            binding.visitInfo.text = getString(
                                R.string.checkin_visit_info,
                                timeFormat.format(Date(visit.checkInAt)),
                            )
                        }

                        binding.actionButton.setText(
                            if (state.isCheckedIn) R.string.action_check_out
                            else R.string.action_check_in
                        )
                        binding.actionButton.isEnabled =
                            state.isCheckedIn || state.canProceed
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CheckInEvent.CheckedIn ->
                                Snackbar.make(view, R.string.checkin_success, Snackbar.LENGTH_SHORT).show()

                            CheckInEvent.CheckedOut -> {
                                Snackbar.make(view, R.string.checkout_success, Snackbar.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }

                            is CheckInEvent.TooEarly -> Snackbar.make(
                                view,
                                getString(R.string.checkout_too_early, event.minutesLeft),
                                Snackbar.LENGTH_LONG,
                            ).show()

                            is CheckInEvent.AlreadyOpen -> {
                                // Cùng cửa hàng thì ở lại màn này — state vừa cập
                                // nhật nên nút đã đổi thành Check-out. Khác cửa
                                // hàng thì thoát ra vì ở đây không làm gì được.
                                val message = if (event.isSameCustomer) {
                                    getString(R.string.checkin_already_here)
                                } else {
                                    getString(R.string.checkin_blocked_by_other, event.customerName)
                                }
                                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
                                if (!event.isSameCustomer) findNavController().navigateUp()
                            }

                            is CheckInEvent.Error ->
                                Snackbar.make(view, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Ảnh đã chụp thì chính nó lấp đầy ô, chưa chụp thì ô là một nút lớn.
     *
     * Nhãn "BẮT BUỘC" chỉ hiện khi đang đứng trong bán kính: ra ngoài bán kính
     * thì lượt ghé đi bằng lý do, đòi thêm ảnh lúc đó là bắt người dùng làm việc
     * thừa. Xem CheckInUseCase để biết vì sao quy tắc chia như vậy.
     */
    private fun bindPhoto(binding: FragmentCheckInBinding, state: CheckInUiState) {
        val photo = state.photo

        binding.photoRequiredBadge.isVisible = state.needsPhoto && photo == null
        binding.photoPreview.isVisible = photo != null
        binding.photoPlaceholder.isVisible = photo == null
        binding.retakeButton.isVisible = photo != null

        binding.photoHint.setText(
            if (state.isProcessingPhoto) R.string.checkin_photo_processing
            else R.string.checkin_take_photo
        )

        if (photo != null) {
            // File ảnh mang tên duy nhất cho mỗi lần chụp nên không sợ Coil trả
            // lại ảnh cũ trong cache khi người dùng bấm "Chụp lại".
            binding.photoPreview.load(File(photo.path))
        }
    }

    private fun observeCameraResult() {
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>(CameraFragment.RESULT_PHOTO_PATH)
            ?.observe(viewLifecycleOwner) { path ->
                // Xoá ngay sau khi nhận: giữ lại thì mỗi lần quay về màn này
                // LiveData phát lại giá trị cũ và ảnh bị xử lý thêm một lần nữa.
                findNavController().currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<String>(CameraFragment.RESULT_PHOTO_PATH)

                viewModel.onPhotoCaptured(path)
            }
    }

    /**
     * Khoảng cách là con số to nhất trên màn, còn lời giải thích nằm ở dải cảnh
     * báo bên dưới — nhìn một cái là biết đứng gần hay xa, đọc thêm mới biết vì
     * sao chưa check-in được.
     */
    private fun renderValidation(
        binding: FragmentCheckInBinding,
        validation: CheckInValidation,
    ) {
        binding.warningText.isVisible = false
        binding.distanceText.text = getString(R.string.value_empty)

        when (validation) {
            is CheckInValidation.Valid -> {
                binding.locationStatus.setText(R.string.checkin_ready)
                validation.distanceMeters?.let {
                    binding.distanceText.text =
                        getString(R.string.checkin_meters, it.roundToInt())
                }
            }

            is CheckInValidation.OverDistance -> {
                binding.locationStatus.setText(R.string.checkin_over_distance)
                binding.distanceText.text =
                    getString(R.string.checkin_meters, validation.distanceMeters.roundToInt())
                binding.warningText.isVisible = true
                binding.warningText.text = getString(
                    R.string.checkin_over_distance_detail,
                    validation.distanceMeters.roundToInt(),
                    validation.allowedMeters.roundToInt(),
                )
            }

            is CheckInValidation.AccuracyTooLow -> {
                binding.locationStatus.setText(R.string.checkin_waiting_gps)
                binding.warningText.isVisible = true
                binding.warningText.text = getString(
                    R.string.checkin_accuracy_too_low,
                    validation.accuracy.roundToInt(),
                    validation.required.roundToInt(),
                )
            }

            CheckInValidation.MockLocation -> {
                binding.locationStatus.setText(R.string.checkin_blocked)
                binding.warningText.isVisible = true
                binding.warningText.setText(R.string.checkin_mock_location)
            }

            CheckInValidation.NoCustomerLocation -> {
                binding.locationStatus.setText(R.string.checkin_ready)
                binding.warningText.isVisible = true
                binding.warningText.setText(R.string.checkin_no_customer_location)
            }

            CheckInValidation.NoLocation ->
                binding.locationStatus.setText(R.string.checkin_waiting_gps)
        }
    }

    private fun requestPermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun showPermissionDenied() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.checkin_permission_title)
            .setMessage(R.string.checkin_permission_message)
            // Từ chối kèm "Không hỏi lại" thì hệ thống bỏ qua mọi lần xin sau;
            // đường duy nhất là mở Cài đặt ứng dụng.
            .setPositiveButton(R.string.checkin_open_settings) { _, _ ->
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", requireContext().packageName, null),
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> findNavController().navigateUp() }
            .show()
    }

    /** Lưu mức pin cùng lượt ghé — máy sắp hết pin thường cho GPS kém chính xác. */
    private fun batteryPercent(): Int? =
        (requireContext().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    override fun onStop() {
        super.onStop()
        // Ngừng nhận vị trí khi rời màn hình — GPS độ chính xác cao rất tốn pin.
        viewModel.stopLocation()
    }
}
