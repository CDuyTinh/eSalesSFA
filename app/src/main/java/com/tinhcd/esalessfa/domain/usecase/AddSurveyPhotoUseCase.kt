package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.repository.LocationSource
import com.tinhcd.esalessfa.domain.repository.PhotoUploader
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/**
 * Thêm ảnh minh chứng: lấy toạ độ đóng dấu, lưu ảnh, rồi kích hàng đợi upload.
 *
 * Upload ngay chứ không đợi lúc nộp bài: ảnh lên dần trong khi nhân viên còn trả
 * lời tiếp, nên lúc bấm hoàn tất thường đã xong hết.
 */
class AddSurveyPhotoUseCase @Inject constructor(
    private val surveyRepository: SurveyRepository,
    private val locationSource: LocationSource,
    private val photoUploader: PhotoUploader,
) {

    suspend operator fun invoke(
        surveyId: String,
        questionId: String?,
        rawFile: File,
        customerName: String,
    ) {
        surveyRepository.addPhoto(
            surveyId = surveyId,
            questionId = questionId,
            rawFile = rawFile,
            location = currentLocation(),
            customerName = customerName,
        )
        photoUploader.start()
    }

    /**
     * Chờ toạ độ tối đa 3 giây rồi thôi.
     *
     * Không có toạ độ thì ảnh vẫn phải chụp được, chỉ là dấu thiếu một dòng.
     * Chặn nhân viên đứng đợi GPS giữa cửa hàng thì tệ hơn nhiều.
     */
    private suspend fun currentLocation(): GeoPoint? =
        if (!locationSource.hasPermission()) {
            null
        } else {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                locationSource.locationUpdates().firstOrNull()?.point
            }
        }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
