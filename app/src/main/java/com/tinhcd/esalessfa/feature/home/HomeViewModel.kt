package com.tinhcd.esalessfa.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.usecase.ResolveStartDestinationUseCase
import com.tinhcd.esalessfa.domain.usecase.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Sự kiện một lần — không đưa vào state để tránh phát lại khi xoay máy. */
sealed interface HomeEvent {
    /** Máy đã qua ngày mới trong lúc app còn mở: cần đồng bộ lại. */
    data object SyncNewDay : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val resolveStartDestination: ResolveStartDestinationUseCase,
) : ViewModel() {

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Chỉ nhắc MỘT lần mỗi lần chạy app.
     *
     * Người dùng bấm Back thoát khỏi màn đồng bộ mà mốc ngày chưa được ghi, nên
     * lần onResume kế tiếp vẫn thấy "đã qua ngày mới". Không có cờ này thì Home
     * đẩy họ trở lại màn đồng bộ ngay, và không còn đường nào ra.
     */
    private var hasPrompted = false

    /**
     * Bắt trường hợp app nằm mở xuyên qua nửa đêm.
     *
     * Splash chỉ chạy lúc khởi động nguội, mà nhân viên thường để app chạy suốt
     * ngày. Hỏi lại đúng quy tắc của Splash mỗi lần Home hiện ra thì tuyến bán
     * hàng và giá của ngày mới không bị bỏ sót.
     */
    fun onResumed() {
        if (hasPrompted) return

        viewModelScope.launch {
            if (resolveStartDestination() != StartDestination.DAILY_SYNC) return@launch
            hasPrompted = true
            _events.send(HomeEvent.SyncNewDay)
        }
    }
}
