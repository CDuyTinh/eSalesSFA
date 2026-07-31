package com.tinhcd.esalessfa.core.file

import android.content.Context
import com.tinhcd.esalessfa.core.common.dispatcher.DispatcherProvider
import com.tinhcd.esalessfa.domain.repository.ReportFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hiện thực [ReportFileStore]: ghi vào cache/reports — đúng thư mục khai trong
 * res/xml/file_paths.xml nên FileProvider chia sẻ được.
 *
 * Cache chứ không phải bộ nhớ trong: hệ thống tự dọn khi thiếu dung lượng, và
 * báo cáo chỉ cần tồn tại đủ lâu để gửi đi.
 */
@Singleton
class CacheReportFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ReportFileStore {

    override suspend fun write(fileName: String, content: String): String =
        withContext(dispatchers.io) {
            val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            file.absolutePath
        }

    private companion object {
        const val DIR_NAME = "reports"
    }
}
