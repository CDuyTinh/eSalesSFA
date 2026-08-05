package com.tinhcd.esalessfa.feature.customer.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.survey.SurveyTypeInfo
import com.tinhcd.esalessfa.domain.model.visit.VisitGate
import com.tinhcd.esalessfa.domain.usecase.LoadCustomerDetailUseCase
import com.tinhcd.esalessfa.domain.usecase.ObserveVisitGateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val loadCustomerDetail: LoadCustomerDetailUseCase,
    private val observeVisitGate: ObserveVisitGateUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = savedStateHandle[CustomerDetailFragment.ARG_CUSTOMER_ID] ?: ""

    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer.asStateFlow()

    private val _surveyTypes = MutableStateFlow<List<SurveyTypeInfo>>(emptyList())
    val surveyTypes: StateFlow<List<SurveyTypeInfo>> = _surveyTypes.asStateFlow()

    /** Cổng nghiệp vụ tại cửa hàng này — xem [ObserveVisitGateUseCase]. */
    val gate: StateFlow<VisitGate> = observeVisitGate(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitGate.CanCheckIn)

    init {
        viewModelScope.launch {
            val detail = loadCustomerDetail(customerId)
            _customer.value = detail.customer
            _surveyTypes.value = detail.surveyTypes
        }
    }
}
