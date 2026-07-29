package com.tinhcd.esalessfa.feature.visit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.core.location.LocationProvider
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.OpenVisit
import com.tinhcd.esalessfa.domain.repository.ReasonCode
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.visit.CheckInValidation
import com.tinhcd.esalessfa.domain.visit.CheckInValidator
import com.tinhcd.esalessfa.domain.visit.LocationSample
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
    data class BlockedByOther(val customerName: String) : CheckInEvent
}

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val visitRepository: VisitRepository,
    private val locationProvider: LocationProvider,
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
            _uiState.update {
                it.copy(
                    customer = customerRepository.getById(customerId),
                    config = visitRepository.checkInConfig(),
                    openVisit = visitRepository.openVisit(customerId),
                    reasonCodes = visitRepository.reasonCodes(
                        VisitRepository.REASON_OVER_DISTANCE
                    ),
                )
            }
        }
    }

    /** Bắt đầu lấy vị trí. Gọi sau khi người dùng đã cấp quyền. */
    fun startLocation() {
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            locationProvider.bestLocation().collect { sample ->
                val state = _uiState.value
                _uiState.update {
                    it.copy(
                        sample = sample,
                        isWaitingLocation = false,
                        validation = CheckInValidator.validate(
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
        if (!state.canProceed) return
        if (state.needsReason && reasonCode.isNullOrBlank()) return

        viewModelScope.launch {
            // Chặn phòng thủ: màn chi tiết đã khoá nút, nhưng nếu người dùng mở
            // hai màn check-in liên tiếp trước khi trạng thái kịp cập nhật thì
            // vẫn có thể lọt. Hai lượt ghé chồng nhau sẽ khiến không lượt nào có
            // giờ ra đúng.
            val active = visitRepository.observeActiveVisit().first()
            if (active != null && active.customerId != customerId) {
                _events.send(CheckInEvent.BlockedByOther(active.customerName))
                return@launch
            }

            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                visitRepository.checkIn(
                    customerId = customerId,
                    sample = state.sample,
                    distanceMeters = state.validation.distanceOrNull(),
                    reasonCode = reasonCode,
                    batteryPct = batteryPct,
                )
            }.onSuccess { visitId ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        openVisit = OpenVisit(visitId, customerId, System.currentTimeMillis()),
                    )
                }
                _events.send(CheckInEvent.CheckedIn(visitId))
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false) }
                _events.send(CheckInEvent.Error(e.message ?: "Không check-in được"))
            }
        }
    }

    fun checkOut(note: String?) {
        val state = _uiState.value
        val visit = state.openVisit ?: return

        val now = System.currentTimeMillis()
        if (!CheckInValidator.canCheckOut(visit.checkInAt, now, state.config)) {
            val left = state.config.minVisitMinutes - ((now - visit.checkInAt) / 60_000L).toInt()
            viewModelScope.launch { _events.send(CheckInEvent.TooEarly(left.coerceAtLeast(1))) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                visitRepository.checkOut(
                    visitId = visit.id,
                    sample = state.sample,
                    distanceMeters = state.validation.distanceOrNull(),
                    note = note,
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, openVisit = null) }
                _events.send(CheckInEvent.CheckedOut)
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

    private fun CheckInValidation.distanceOrNull(): Double? = when (this) {
        is CheckInValidation.Valid -> distanceMeters
        is CheckInValidation.OverDistance -> distanceMeters
        else -> null
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
    }
}
