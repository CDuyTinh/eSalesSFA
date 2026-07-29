package com.tinhcd.esalessfa.core.network.api

import com.tinhcd.esalessfa.core.network.dto.SyncDownloadRequest
import com.tinhcd.esalessfa.core.network.dto.SyncDownloadResponse
import com.tinhcd.esalessfa.core.network.dto.SyncUploadRequest
import com.tinhcd.esalessfa.core.network.dto.SyncUploadResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Hợp đồng với Edge Functions. App không biết gì về cấu trúc bảng — chỉ biết
 * hai thao tác nghiệp vụ: kéo thay đổi về, và đẩy giao dịch lên.
 */
interface SyncApi {

    /**
     * Kéo dữ liệu master thay đổi kể từ mốc version client đang giữ.
     * Server trả về nhiều bảng trong MỘT response để tránh hàng chục round-trip.
     */
    @POST("sync-download")
    suspend fun download(@Body request: SyncDownloadRequest): Response<SyncDownloadResponse>

    /**
     * Đẩy batch giao dịch tạo offline lên server.
     *
     * Idempotent: gửi lại cùng `sessionId` thì server nhận ra và không tạo bản ghi
     * trùng — điều kiện bắt buộc khi mạng chập chờn và client phải retry.
     */
    @POST("sync-upload")
    suspend fun upload(@Body request: SyncUploadRequest): Response<SyncUploadResponse>
}
