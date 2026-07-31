package com.tinhcd.esalessfa.domain.repository

/**
 * Cổng ghi file báo cáo ra chỗ có thể chia sẻ đi.
 *
 * ViewModel không được biết cacheDir hay FileProvider nằm ở đâu; nó chỉ đưa nội
 * dung và nhận lại đường dẫn. Việc dựng Uri chia sẻ vẫn thuộc về Fragment vì đó
 * là chuyện của Intent, không phải của dữ liệu.
 */
interface ReportFileStore {

    /** Ghi [content] thành file [fileName] và trả về đường dẫn tuyệt đối. */
    suspend fun write(fileName: String, content: String): String
}
