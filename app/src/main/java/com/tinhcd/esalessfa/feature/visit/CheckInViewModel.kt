package com.tinhcd.esalessfa.feature.visit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.repository.LocationSource
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.OpenVisit
import com.tinhcd.esalessfa.domain.repository.ReasonCode
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.usecase.CheckInOutcome
import com.tinhcd.esalessfa.domain.usecase.CheckInUseCase
import com.tinhcd.esalessfa.domain.usecase.CheckOutResult
import com.tinhcd.esalessfa.domain.usecase.CheckOutUseCase
import com.tinhcd.esalessfa.domain.usecase.LoadCheckInContextUseCase
import com.tinhcd.esalessfa.domain.usecase.ValidateCheckInUseCase
import com.tinhcd.esalessfa.domain.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.visit.CheckInValidation
import com.tinhcd.esalessfa.domain.visit.LocationSample
import com.tinhcd.esalessfa.domain.visit.distanceMetersOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckInUiState(
    val customer: Customer? = null,
    val sample: LocationSample? = null,
    val validation: CheckInValidation = CheckInValidation.NoLocation,
    val config: CheckInConfig = CheckInConfig(),
    val openVisit: OpenVisit? = null,
    val reasonCodes: List<ReasonCode> = emptyList(),
    val isWaitingLocation: Boolean = true,
    val isSaving: Boolean = false,
) {
    val isCheckedIn: Boolean get() = openVisit != null

    /** Ngoài bán kính vẫn cho đi tiếp, nhưng phải kèm lý do. */
    val needsReason: Boolean get() = validation is CheckInValidation.OverDistance

    val canProceed: Boolean
        get() = !isSaving &&
            (validation is CheckInValidation.Valid || validation is CheckInValidation.OverDistance)
}

sealed interface CheckInEvent {
    data class CheckedIn(val visitId: String) : CheckInEvent
    data object CheckedOut : CheckInEvent
    data class Error(val message: String) : CheckInEvent
    data class TooEarly(val minutesLeft: Int) : CheckInEvent
    /** Đã có lượt ghé đang mở — ở đây hoặc nơi khác. */
    data class AlreadyOpen(val customerName: String, val isSameCustomer: Boolean) : CheckInEvent
}

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
    private val locationSource: LocationSource,
    private val loadCheckInContext: LoadCheckInContextUseCase,
    private val validateCheckIn: ValidateCheckInUseCase,
    private val checkInUseCase: CheckInUseCase,
    private val checkOutUseCase: CheckOutUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = savedStateHandle[ARG_CUSTOMER_ID] ?: ""

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val _events = Channel<CheckInEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            val context = loadCheckInContext(customerId)
            _uiState.update {
                it.copy(
                    customer = context.customer,
                    config = context.config,
                    reasonCodes = context.reasonCodes,
                )
            }
        }

        // QUAN SÁT lượt ghé thay vì đọc một lần lúc khởi tạo. Đọc một lần thì
        // user bấm nhanh trước khi truy vấn xong sẽ thấy nút vẫn là "Check-in"
        // dù đã ghé rồi.
        viewModelScope.launch {
            visitRepository.observeOpenVisit(customerId).collect { visit ->
                _uiState.update { it.copy(openVisit = visit) }
            }
        }
    }

    /** Bắt đầu lấy vị trí. Gọi sau khi người dùng đã cấp quyền. */
    fun startLocation() {
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            locationSource.bestLocation().collect { sample ->
                val state = _uiState.value
                _uiState.update {
                    it.copy(
                        sample = sample,
                        isWaitingLocation = false,
                        validation = validateCheckIn(
                            sample = sample,
                            customerLocation = state.customer?.location,
                            config = state.config,
                        ),
                    )
                }
            }
        }
    }

    fun stopLocation() {
        locationJob?.cancel()
        locationJob = null
    }

    fun checkIn(reasonCode: String?, batteryPct: Int?) {
        val state = _uiState.value
        if (state.isSaving) return

        viewModelScope.launch {
            // Điều kiện "được phép check-in" do use case quyết, ViewModel không
            // xét lại: màn chi tiết đã khoá nút, nhưng nếu người dùng mở hai màn
            // check-in liên tiếp trước khi trạng thái kịp cập nhật thì vẫn có thể
            // lọt, và hai lượt ghé chồng nhau sẽ khiến không lượt nào có giờ ra
            // đúng.
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                checkInUseCase(
                    customerId = customerId,
                    sample = state.sample,
                    validation = state.validation,
                    reasonCode = reasonCode,
                    batteryPct = batteryPct,
                )
            }.onSuccess { outcome ->
                _uiState.update { it.copy(isSaving = false) }
                when (outcome) {
                    is CheckInOutcome.Success ->
                        _events.send(CheckInEvent.CheckedIn(outcome.visitId))

                    is CheckInOutcome.AlreadyOpen ->
                        _events.send(
                            CheckInEvent.AlreadyOpen(
                                customerName = outcome.customerName,
                                isSameCustomer = outcome.isSameCustomer,
                            )
                        )

                    // Nút đã khoá theo canProceed nên nhánh này chỉ xảy ra khi vị
                    // trí đổi ngay giữa lúc bấm — im lặng là đúng, người dùng sẽ
                    // thấy trạng thái mới trên màn hình.
                    CheckInOutcome.Rejected -> Unit
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false) }
                _events.send(CheckInEvent.Error(e.message ?: "Không check-in được"))
            }
        }
    }

    fun checkOut(note: String?) {
        val state = _uiState.value
        val visit = state.openVisit ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                checkOutUseCase(
                    visit = visit,
                    config = state.config,
                    sample = state.sample,
                    distanceMeters = state.validation.distanceMetersOrNull,
                    note = note,
                )
            }.onSuccess { result ->
                _uiState.update { it.copy(isSaving = false) }
                when (result) {
                    CheckOutResult.Done -> {
                        _uiState.update { it.copy(openVisit = null) }
                        _events.send(CheckInEvent.CheckedOut)
                    }

                    is CheckOutResult.TooEarly ->
                        _events.send(CheckInEvent.TooEarly(result.minutesLeft))
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false) }
                _events.send(CheckInEvent.Error(e.message ?: "Không check-out được"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocation()
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
    }
}
