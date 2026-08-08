package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.AuthRepository
import com.tinhcd.esalessfa.domain.repository.SessionStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Bốn đích đến khả dĩ khi mở app. */
enum class StartDestination { LOGIN, FIRST_SYNC, DAILY_SYNC, HOME }

/**
 * Quyết định mở app vào đâu.
 *
 * Là quy tắc nghiệp vụ chứ không phải chuyện điều hướng:
 * - Chưa sync lần nào thì mọi màn hình đều trống trơn — UI chỉ đọc từ Room — nên
 *   phải đi qua màn đồng bộ trước.
 * - Lượt sync gần nhất từ hôm trước thì tuyến bán hàng, tồn kho và giá của hôm
 *   nay chưa có trên máy. Nhân viên mở app buổi sáng mà đi theo tuyến hôm qua là
 *   sai nghiệp vụ, nên chặn lại ở màn đồng bộ chứ không thả vào Home.
 *
 * Home cũng hỏi lại đúng quy tắc này ở onResume, cho trường hợp app nằm mở
 * xuyên qua nửa đêm và Splash không có dịp chạy.
 */
class ResolveStartDestinationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
) {

    /**
     * [today] có mặc định để test tự chọn ngày, giống [ObserveDashboardUseCase].
     *
     * Mốc so sánh là đồng hồ thiết bị. Máy bị chỉnh lùi ngày sẽ ra `lastSync >
     * today` và rơi vào [StartDestination.HOME] — chấp nhận được, vì bản Java cũ
     * cũng chỉ hỏi "lượt sync gần nhất có trước 0h hôm nay không".
     */
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): StartDestination {
        if (!authRepository.isLoggedIn()) return StartDestination.LOGIN

        val lastSync = sessionStore.lastSyncDate.first()
        return when {
            lastSync == null -> StartDestination.FIRST_SYNC
            lastSync.isBefore(today) -> StartDestination.DAILY_SYNC
            else -> StartDestination.HOME
        }
    }
}
