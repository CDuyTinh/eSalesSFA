package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.visit.VisitGate
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cổng nghiệp vụ tại một cửa hàng, theo dõi liên tục.
 *
 * Hai quy tắc Sales Step gộp lại: chỉ thao tác được khi đã check-in tại đúng cửa
 * hàng này, và mỗi thời điểm chỉ ghé được MỘT cửa hàng. Đang dở ở nơi khác thì
 * ngay cả nút check-in ở đây cũng phải khoá, nếu không nhân viên sẽ mở hai lượt
 * ghé chồng nhau và không lượt nào có giờ ra đúng.
 *
 * Trước đây phép ánh xạ này nằm trong ViewModel của màn chi tiết khách hàng, nên
 * màn nào cần cổng cũng phải chép lại đúng ba nhánh.
 */
class ObserveVisitGateUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
) {

    operator fun invoke(customerId: String): Flow<VisitGate> =
        visitRepository.observeActiveVisit().map { active ->
            when {
                active == null -> VisitGate.CanCheckIn
                active.customerId == customerId -> VisitGate.CheckedInHere(active)
                else -> VisitGate.BlockedByOther(active)
            }
        }
}
