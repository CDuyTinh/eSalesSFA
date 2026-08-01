# Luồng đồng bộ: `sync-download` & `sync-upload`

Ghi chú chi tiết hai chức năng của `SyncApi`, đi từ UI xuống Edge Function và ngược lại.

- **Hợp đồng API**: `app/src/main/java/com/tinhcd/esalessfa/core/network/api/SyncApi.kt`
- **DTO**: `core/network/dto/SyncDto.kt`
- **Repository**: `data/repository/SyncRepositoryImpl.kt`
- **Worker / Manager**: `core/sync/SyncWorker.kt`, `core/sync/SyncManager.kt`
- **UI**: `feature/sync/SyncViewModel.kt`
- **Server**: `supabase/functions/sync-download/index.ts`, `supabase/functions/sync-upload/index.ts`, `supabase/functions/_shared/auth.ts`

---

## 0. Nền tảng chung

### Transport
`NetworkModule` dựng Retrofit với `baseUrl = "${SUPABASE_URL}/functions/v1/"`, nên hai `@POST("sync-download")` / `@POST("sync-upload")` trỏ đúng vào Edge Function. Timeout read/write **120s** (sync đầu ca kéo hàng chục nghìn dòng), connect 30s. `Json` cấu hình `ignoreUnknownKeys = true` — server thêm cột mới không làm client cũ crash.

`AuthInterceptor` gắn vào **mọi** request:
- `apikey: <SUPABASE_PUBLISHABLE_KEY>`
- `Authorization: Bearer <jwt>` (đọc qua `runBlocking { auth.currentAccessTokenOrNull() }`)

Thiếu header → Supabase gateway chặn 401 **trước khi** code function chạy.

### Nhận diện người gọi (server)
`resolveCaller(req, db)` trong `_shared/auth.ts`:
1. Bắt buộc `Authorization: Bearer …`, không có → `401 MISSING_TOKEN`.
2. `db.auth.getUser(token)` → lỗi → `401 INVALID_TOKEN`.
3. Tra `salespersons` theo `user_id` + `is_deleted = false` → không có → `403 NO_SALESPERSON` (kèm câu SQL gợi ý để nối user với hồ sơ nhân viên).
4. Trả `Caller { userId, salespersonId, branchId, role }`.

Cả hai function dùng `adminClient()` (service_role, **bỏ qua RLS**), nên **toàn bộ phân quyền phải tự làm thủ công** trong code function — đây là điểm cần cẩn thận nhất khi sửa hai file này.

### Cơ chế versioning (nền của download)
`migrations/0001_init_schema.sql`:
- Một sequence toàn cục `global_version_seq`.
- `fn_setup_sync_table(t)` gắn trigger `fn_set_row_version()` (mỗi INSERT/UPDATE gán `row_version := nextval(...)`) + tạo index trên `row_version`.
- Soft delete bằng cột `is_deleted`, không DELETE thật — nếu xoá cứng thì client không có cách nào biết dòng đó đã mất.

Vì sequence là **toàn cục** (không phải per-table), `row_version` tăng đơn điệu trên cả DB → client chỉ cần hỏi "cho tôi các dòng có `row_version` > mốc tôi đang giữ".

Bảng master được gắn trigger ở `0001`; `orders` / `order_details` được thêm cột + gắn trigger sau ở `0003_inventory_survey.sql:157-168` (ban đầu bảng giao dịch không cần versioning vì chỉ đẩy lên, sau đó mới tải lịch sử đơn về cho dashboard offline).

### Bảng `sync_sessions`
```
session_id      uuid PRIMARY KEY
salesperson_id  uuid NOT NULL
sync_type       'DOWNLOAD' | 'UPLOAD'
status          'PROCESSING' | 'SUCCESS' | 'FAILED'
payload_summary jsonb          -- upload: { accepted, rejected }
started_at / finished_at
```
`session_id` là PRIMARY KEY — chính là chốt idempotency của upload.

---

## 1. `sync-download` — kéo master data về

### 1.1 Ai kích hoạt

| Đường vào | Cách gọi | Chính sách |
|---|---|---|
| Sau đăng nhập (`SyncMode.FIRST_RUN`) | `SyncManager.startDownload()` | `ExistingWorkPolicy.KEEP` — bấm lại / xoay máy không tạo lượt mới |
| Retry của FIRST_RUN | `startDownload(force = true)` | `APPEND_OR_REPLACE` — xếp sau lượt đang chạy |
| User bấm Đồng bộ (`SyncMode.MANUAL`) | `startFullSync()` | Chuỗi **upload → download**, `APPEND_OR_REPLACE` |

`startFullSync()` dùng `beginUniqueWork(WORK_FULL, …, upload).then(download)`. **Thứ tự upload trước là cố ý**: download trước có thể ghi đè master data (giá, khuyến mãi) mà đơn đang chờ gửi tham chiếu tới, làm server đối chiếu giá và từ chối đơn vốn hợp lệ lúc tạo.

`startFullSync()` trả `FullSyncHandle(uploadId, downloadId)` để UI theo dõi **đúng lượt vừa xếp** — quan sát theo tên unique work sẽ thấy cả kết quả `SUCCEEDED` của lượt trước còn lưu lại và màn hình báo "xong" ngay khi vừa mở.

Constraint của cả hai work: `NetworkType.CONNECTED`, backoff `EXPONENTIAL` từ 30s.

### 1.2 `SyncDownloadWorker.doWork()`
Collect `syncRepository.downloadMasterData()` và dịch `SyncProgress` sang `WorkInfo`:
- `Downloading` → `setProgress(page, total_rows, table)`
- `Completed` → `Result.success(total_rows, page, duration_ms)`
- `Failed` → `Result.retry()` nếu `isRetryable`, ngược lại `Result.failure(error)`

### 1.3 `SyncRepositoryImpl.downloadMasterData()` — vòng lặp phân trang

Chạy trên `dispatchers.io`, bọc trong `syncMutex`. Nếu mutex đang khoá → emit `Failed("Đang có một lượt đồng bộ khác chạy", isRetryable = false)` và thoát. (WorkManager đã chống trùng ở mức công việc, nhưng vẫn còn đường gọi trực tiếp từ UI — mutex bịt nốt khe đó.)

Mỗi vòng lặp (`while (hasMore)`):

1. **Đọc lại mốc version** từ `sync_state`: `syncStateDao.getAll().associate { tableName to lastVersion }`. Đọc lại mỗi vòng vì trang trước vừa ghi xong đã cập nhật mốc.
2. Gọi `api.download(SyncDownloadRequest(sessionId = UUID.randomUUID(), versions, pageSize = 1000))`.
   > Lưu ý: `sessionId` sinh mới **mỗi trang**, nên một lượt sync N trang tạo N dòng trong `sync_sessions`. Download không cần idempotency theo session (chỉ dùng để log/audit).
3. Kiểm tra response:
   - `!isSuccessful` → `Failed`, `isRetryable = code >= 500`. 4xx là lỗi phía client (token hỏng, chưa nối nhân viên) — retry vô ích.
   - `body == null` → `Failed(isRetryable = true)`.
4. **Ghi cả trang trong MỘT `db.withTransaction`**: loop `body.tables` gọi `writeTable(table, changeSet)`. Hoặc vào trọn vẹn, hoặc không gì cả → mốc version chỉ tiến khi dữ liệu đã nằm trong DB, mất mạng giữa chừng thì lần sau tải lại đúng phần còn thiếu.
5. `hasMore = body.hasMore`, emit `Downloading(page, rowsThisPage, totalRows, currentTable)`.
6. Chặn an toàn: `page >= MAX_PAGES (50)` → `Failed("Vượt quá 50 trang — nghi ngờ vòng lặp không kết thúc", isRetryable = false)`.

Kết thúc: emit `Completed(totalRows, pages, durationMs)`.

Hằng số: `PAGE_SIZE = 1000` (khớp trần `db-max-rows` của PostgREST), `MAX_PAGES = 50`.

### 1.4 `writeTable()` — giải mã theo bảng

`TableChangeSet.rows` là `List<JsonElement>` (JSON thô), tầng data mới decode sang DTO cụ thể → thêm bảng mới không phải sửa lớp response.

```
rows.decode<XxxDto>().map { it.toEntity() } → masterDao.upsertXxx(...)
```

`when (table)` phủ 23 bảng trong `SyncTables`. Nhóm có xử lý `deletedIds` (server soft-delete, client xoá cứng khỏi máy): `customers`, `sales_route_details`, `products`, `price_lists`, `promotion_programs`.

Trường hợp riêng:
- `orders` → `OrderDownloadDto.toEntity(now)`, trong đó **`syncStatus = SYNCED`, `serverAckAt = now`**. Vì `@Upsert` thay cả dòng, đơn tải về luôn ở trạng thái SYNCED — hợp lý vì nó đã nằm trên server.
- `order_details` → `OrderDetailDownloadDto.toEntity()`.
- `else -> return 0`: **bảng lạ bị bỏ qua thay vì ném lỗi**, để app phiên bản cũ vẫn sync được sau khi server nâng cấp. Hệ quả: bảng lạ không cập nhật `sync_state`, nên server sẽ gửi lại bảng đó ở mọi lượt sau (đến khi client biết bảng đó). `MAX_PAGES` là cái chặn nếu bảng lạ đủ lớn để luôn báo `has_more`.

Sau khi ghi xong, cập nhật mốc:
```kotlin
syncStateDao.upsert(SyncStateEntity(tableName = table, lastVersion = changeSet.maxVersion,
                                   lastSyncedAt = now, rowCount = rows.size))
```

### 1.5 Phía server: `sync-download/index.ts`

1. Chỉ nhận `POST`, không thì `405 METHOD_NOT_ALLOWED`.
2. `resolveCaller` → thiếu `session_id` thì `400 MISSING_SESSION`.
3. **Cắt trần page size**: `pageSize = min(body.page_size ?? 1000, 1000)`. Đây là chỗ dễ sai: PostgREST chặn cứng 1000 dòng/request bất kể `.limit()` đặt bao nhiêu; nếu để `pageSize > 1000` thì phép kiểm `rows.length >= pageSize` không bao giờ đúng → server báo `has_more = false` trong khi dữ liệu đã bị cắt cụt → **client mất dữ liệu mà không biết**.
4. `upsert` một dòng `sync_sessions` với `status = 'PROCESSING'`, `sync_type = 'DOWNLOAD'`.
5. **Tải song song mọi bảng** bằng `Promise.all` trên `SYNC_TABLES`:
   - `since = versions[table] ?? 0`
   - `rows = fetchChanges(db, table, scope, caller, since, pageSize)`
   - `rows.length === 0` → **bỏ hẳn bảng khỏi response** (đỡ băng thông; client cũng không cập nhật mốc cho bảng đó)
   - `maxVersion = max(row_version)` tính **trên toàn bộ rows kể cả dòng đã xoá**, nên mốc không bị tụt
   - `rows.length >= pageSize` → `hasMore = true` (cùng lắm thừa một lượt gọi trả rỗng)
   - Tách `rows` (`!is_deleted`) và `deleted_ids` (`is_deleted`, lấy `id ?? code`)
6. Cập nhật session `status = 'SUCCESS'`, `finished_at`.
7. Trả `{ session_id, tables, has_more, server_time }`.

> `hasMore` là phép OR trên mọi bảng: một bảng đầy trang thì cả lượt được gọi lại, các bảng đã hết chỉ trả rỗng.

### 1.6 `SYNC_TABLES` — whitelist + scope

Đây là whitelist: client gửi tên bảng lạ sẽ bị bỏ qua, không thể dùng endpoint để dò bảng khác trong database.

| Scope | Bảng | Cách lọc |
|---|---|---|
| `ALL` | `app_configs`, `uoms`, `branches`, `channels`, `price_groups`, `reason_codes`, `product_categories`, `products`, `product_uoms`, `price_lists`, `promotion_programs`, `promotion_breaks`, `promotion_items`, `survey_types`, `survey_question_groups`, `survey_questions`, `survey_question_options` | không lọc thêm |
| `BY_SALESPERSON` | `salespersons`, `sales_routes`, `orders` | `salespersons` lọc `id = caller`, còn lại `salesperson_id = caller`. `orders` thêm `order_date >= today - 90` |
| `BY_ROUTE` | `customers`, `sales_route_details` | tra `sales_routes` của nhân viên → `routeIds`; `sales_route_details` lọc `route_id IN routeIds`; `customers` tra thêm `customer_id` từ route details rồi lọc `id IN customerIds`. `routeIds` rỗng → trả `[]` ngay |
| `BY_ORDER` | `order_details` | **Lọc qua quan hệ**, không gom id |

Query gốc của `fetchChanges`:
```ts
db.from(table).select('*')
  .gt('row_version', since)
  .order('row_version', { ascending: true })
  .limit(limit)
```

**`BY_ORDER` đáng chú ý**: cách gom `orderIds` rồi `.in()` sẽ nhét ~720 UUID vào query string (~27KB) và PostgREST trả 400. Thay bằng
```ts
.select('*, orders!inner(id)').eq('orders.salesperson_id', …).gte('orders.order_date', …)
```
cú pháp `orders!inner` đẩy phép lọc xuống thành JOIN trong SQL → URL giữ nguyên độ dài bất kể lịch sử bao nhiêu đơn.

Lỗi DB được ném `500 DB_ERROR` kèm `[message, details, hint].join(' | ')` — PostgREST để nguyên nhân thật trong `details`/`hint`, chỉ lấy `message` thì nhận được "Bad Request" và không lần ra được gì.

`HISTORY_DAYS = 90`: đủ cho dashboard tháng và báo cáo, tải toàn bộ lịch sử nhiều năm vào SQLite điện thoại là vô nghĩa.

---

## 2. `sync-upload` — đẩy giao dịch offline lên

### 2.1 Ai kích hoạt
- **Tự động** sau mỗi lần chốt việc: `OrderEditViewModel:171`, `StockCountViewModel:92`, `SurveyFormViewModel:154` → `SyncManager.startUpload()` (`APPEND_OR_REPLACE`). Có mạng thì lên ngay, không mạng thì WorkManager giữ lại và tự chạy khi kết nối trở lại.
- **Thủ công** qua `startFullSync()` (bước đầu của chuỗi).

### 2.2 Outbox là một QUERY, không phải bảng riêng
Trạng thái đồng bộ ghi ngay trên bản ghi nghiệp vụ để tránh dual-write (ghi hai nơi rồi lệch nhau):

```sql
SELECT * FROM <table>
WHERE syncStatus IN ('PENDING','FAILED') AND syncAttempts < 5
ORDER BY clientCreatedAt LIMIT 50
```

`SyncStatus`: `DRAFT` (chưa xác nhận, **không** đẩy lên) → `PENDING` (outbox) → `SYNCING` (worker đang gửi) → `SYNCED` | `FAILED`. Trạng thái chỉ đặt trên **bảng gốc** (`visits`, `orders`, `stock_counts`, `surveys`); bảng con đi theo cha, nếu không sẽ có cảnh đơn đã SYNCED mà vài dòng chi tiết còn PENDING.

`FAILED` vẫn được lấy lại nhưng chặn bởi `syncAttempts < 5`, nên bản ghi lỗi nghiệp vụ không quay vòng vô hạn.

### 2.3 `SyncRepositoryImpl.uploadPending()`

**Hai cửa chặn trước khi làm gì cả:**
1. `uploadMutex.isLocked` → `Failed("Đang có một lượt gửi khác chạy", isRetryable = false)`. Mutex upload **tách riêng** khỏi `syncMutex` — tải xuống và gửi lên không cần chặn nhau.
2. `visitDao.countOpenVisits() > 0` → **`Skipped("Còn cửa hàng chưa check-out")`**.
   Quy tắc nghiệp vụ: đơn hàng / kiểm kê / khảo sát của lượt ghé đang mở vẫn có thể bị sửa cho tới khi check-out, gửi sớm là đẩy số liệu chưa chốt. Chặn ở **tầng repository** chứ không chỉ khoá nút, để auto-upload sau mỗi lần chốt đơn cũng phải tuân theo.

Trong `uploadMutex.withLock`:

1. Gom 4 outbox: `visitDao.getPending()`, `orderDao.getPending()`, `stockCountDao.getPending()`, `surveyResultDao.getPending()`. `total == 0` → `Completed(0, 0, 0)` và thoát.
2. Sinh `sessionId = UUID.randomUUID()`, thu 4 danh sách id.
3. **Đánh dấu `SYNCING` + gắn `sessionId`** cho cả 4 nhóm → hai worker không cùng gửi một bản ghi.
4. Emit `Uploading(sent = 0, total)`.
5. Dựng `SyncUploadRequest`, encode qua `toUploadBody()` thành `JsonElement`:
   - `visits`: `List<JsonElement>`
   - `orders`: `OrderUploadDto { order, details, promotions }`
   - `stockCounts`: `StockCountUploadDto { header, details }`
   - `surveys`: `SurveyUploadDto { header, answers, photos }` (photos là ảnh **đã upload xong** lên storage)
6. `api.upload(request)`.

**Xử lý kết quả:**

| Tình huống | Hành động |
|---|---|
| `Exception` (mạng) | `revertToPending(...)` toàn bộ → `Failed(isRetryable = true)`. Không biết server đã nhận hay chưa, nên trả về PENDING để lượt sau gửi lại; bỏ bước này thì bản ghi **kẹt SYNCING vĩnh viễn** |
| `!isSuccessful` | `revertToPending(...)` → `Failed(isRetryable = code >= 500)` |
| `body == null` | `revertToPending(...)` → `Failed(isRetryable = true)` |
| id ∈ `accepted` | `markSynced(ids, now)` — set `SYNCED`, `serverAckAt`, xoá `lastError` |
| id ∈ `rejected` | ghi `SyncErrorEntity(sessionId, entity, id, "code: message", now)` **và** `markFailed(id, …)` theo `item.entity` (`order` / `visit` / `stock_count` / `survey`). **KHÔNG** đưa về PENDING vì gửi lại vẫn bị từ chối y hệt — phải cho user thấy và xử lý |
| id không nằm trong cả hai (server im lặng bỏ qua) | `revertToPending(...)` phần `filterNot { it in handled }` → lượt sau thử lại, thay vì kẹt SYNCING |

Kết thúc: `Completed(totalRows = accepted.size, pages = 1, durationMs)`.

`markFailed` tăng `syncAttempts + 1`, nên sau 5 lần bản ghi rơi khỏi query outbox.

### 2.4 `SyncUploadWorker.doWork()`
Giống download nhưng có thêm một nhánh quan trọng:
- `Uploading` → `setProgress(total_rows)`
- `Completed` → `Result.success(total_rows)`
- `Failed` → `retry()` / `failure()` theo `isRetryable`
- **`Skipped` → `Result.success(error = reason)`**: bỏ qua vì điều kiện nghiệp vụ được coi là **thành công**, để chuỗi upload→download vẫn chạy tiếp phần tải xuống và WorkManager không ghi nhận một lần thất bại giả.

### 2.5 Phía server: `sync-upload/index.ts`

Ba tính chất bắt buộc ghi rõ ở đầu file: **idempotent**, **validate ở server**, **từ chối có phân loại**.

1. `POST` + `resolveCaller` + bắt buộc `session_id` (như download).
2. **Chốt idempotency**: `INSERT` (không phải upsert) vào `sync_sessions` với `session_id` là PRIMARY KEY.
   - Lỗi trùng khoá → đọc lại `payload_summary` của session cũ và trả về `{ session_id, replayed: true, accepted, rejected }` **thay vì ghi dữ liệu lần nữa**.
   - Lỗi khác → `500 DB_ERROR`.
3. Xử lý **tuần tự từng nhóm**, mỗi item bọc `try/catch` riêng → một item lỗi không làm sập cả batch, nó chỉ rơi vào `rejected`.

   **Visits**: `upsert({ ...visit, salesperson_id: caller.salespersonId, session_id }, { onConflict: 'id', ignoreDuplicates: true })`.
   → **Ép `salesperson_id` theo JWT, không lấy từ payload** — chặn ghi mạo danh. Áp dụng cho cả 4 nhóm.

   **Orders**: `validateOrder(...)` trước; không ok → `rejected` (kèm `code`/`message` từ validate) và `continue`. Ok thì upsert `orders` → `order_details` → `order_promotions`.

   **Stock counts / Surveys**: qua `upsertWithChildren(db, headerTable, header, childTable, children)` — ghi header rồi ghi dòng con, cùng `ignoreDuplicates: true` theo `id`. Surveys ghi thêm `survey_photos`.

4. Cập nhật `sync_sessions`: `status = 'SUCCESS'`, `payload_summary = { accepted, rejected }`, `finished_at`. Chính `payload_summary` này là cái được replay ở bước 2.
5. Trả `{ session_id, accepted, rejected }`.

### 2.6 Hai lớp idempotency

Đây là chỗ dễ hiểu sai nên tách rõ:

- **Lớp session** (`session_id` PRIMARY KEY): retry **cùng** một session → server trả lại kết quả cũ, không ghi lại.
- **Lớp bản ghi** (`onConflict: 'id', ignoreDuplicates: true`): retry với session **khác** cũng không tạo bản ghi thứ hai, vì `id` do client sinh và upsert bỏ qua trùng.

Chính lớp thứ hai là lý do `SyncRepositoryImpl` an tâm sinh `sessionId` mới ở mỗi lượt upload thay vì lưu lại session cũ để retry.

Lưu ý: `ignoreDuplicates: true` nghĩa là bản ghi **đã tồn tại trên server sẽ không bị ghi đè**. Client sửa đơn rồi gửi lại sẽ không cập nhật được — đúng ý đồ (đơn đã chốt là bất biến), nhưng cần nhớ khi thêm luồng sửa-sau-khi-gửi.

### 2.7 `validateOrder()` — không tin số client tính

Client tính tiền để hiển thị ngay khi offline, nhưng con số cuối cùng phải do server xác nhận — nếu không, chỉ cần sửa APK là đặt được hàng giá 0 đồng.

| Kiểm tra | `code` khi fail |
|---|---|
| `details` rỗng | `EMPTY_ORDER` |
| Khách hàng thuộc tuyến của nhân viên (`sales_routes` → `sales_route_details`) | `CUSTOMER_NOT_IN_ROUTE` |
| Khách hàng tồn tại (lấy `price_group_id`) | `CUSTOMER_NOT_FOUND` |
| Mỗi dòng có giá hiện hành trong `price_lists` (`price_group_id` + `product_id` + `uom_code`, `from_date <= today <= to_date`) | `PRICE_NOT_FOUND` |
| `Number(price.price) === Number(d.price)` | `PRICE_MISMATCH` (message gợi ý "Cần đồng bộ lại bảng giá") |

Dòng `is_free_item === true` được bỏ qua (hàng tặng giá 0).

`PRICE_MISMATCH` chính là lý do chuỗi full sync phải **upload trước download** (§1.1).

---

## 3. Ma trận lỗi & retry

| Nguồn | Biểu hiện | `SyncProgress` | `WorkInfo` | Trạng thái bản ghi |
|---|---|---|---|---|
| Mạng đứt | `Exception` | `Failed(retryable)` | `retry()` (backoff 30s exp) | về `PENDING` |
| 5xx | `code >= 500` | `Failed(retryable)` | `retry()` | về `PENDING` |
| 401/403 (token, chưa nối nhân viên) | 4xx | `Failed(non-retryable)` | `failure(error)` | về `PENDING` |
| Lỗi nghiệp vụ | `rejected[]` | không ảnh hưởng | `success()` | `FAILED` + `sync_errors`, **không** retry |
| Còn visit mở | — | `Skipped` | `success()` | giữ `PENDING` |
| Sync trùng | mutex khoá | `Failed(non-retryable)` | `failure()` | không đổi |
| > 50 trang download | vòng lặp nghi vấn | `Failed(non-retryable)` | `failure()` | mốc version đã tiến đến trang cuối ghi được |

`SyncErrorDao` giữ log 7 ngày (`deleteOlderThan`) để bảng không phình vô hạn.

---

## 4. Tầng UI

`SyncViewModel` có hai mode dùng chung một màn hình vì tiến trình và cách báo lỗi giống nhau:

- `FIRST_RUN`: `observeDownload()` → `WorkInfo?.toUiState()`. Xong thì `markFirstSyncCompleted()` rồi vào Home.
- `MANUAL`: `manualHandle.flatMapLatest { observeFullSync(handle) }` → `List<WorkInfo>.toChainUiState(handle)`.

Điểm quan trọng: **state lấy từ WorkManager, không giữ trong ViewModel** — đóng app giữa chừng rồi mở lại vẫn thấy đúng tiến trình đang chạy (ViewModel bị huỷ, công việc thì không).

`toChainUiState` gộp trạng thái cả chuỗi:
- Rỗng → coi là đang chạy (WorkManager chưa ghi xong, tránh nhá qua trạng thái rỗng).
- Có `FAILED` → hiện lỗi; có `CANCELLED` → "Đã huỷ đồng bộ".
- Chỉ báo xong khi **`size == 2 && all SUCCEEDED`** — xét từng việc riêng sẽ báo xong quá sớm.
- Tìm việc download **theo `id`** chứ không lấy phần tử cuối, vì WorkManager không hứa thứ tự.

`start()` với mode `MANUAL` chỉ gọi khi `manualHandle.value == null`: chuỗi dùng `APPEND_OR_REPLACE` nên phải tự chặn gọi trùng (Fragment gọi `start()` lại mỗi lần view được tạo lại).

Màn Sync là **nơi duy nhất** khởi động đồng bộ từ giao diện — trước đó Home tự gọi `startFullSync()` rồi ở lại chỗ cũ nên user bấm mà không thấy gì xảy ra.

`SyncProgress` không dùng phần trăm: số trang chỉ biết được khi server trả `has_more`, nên hiển thị theo trang + số dòng đã ghi trung thực hơn thanh progress giả.

---

## 5. Sơ đồ tóm tắt

```
DOWNLOAD
  SyncViewModel.start()
    → SyncManager.startDownload()            [unique work, KEEP]
      → SyncDownloadWorker
        → SyncRepositoryImpl.downloadMasterData()   [syncMutex, dispatchers.io]
           ┌─ loop while(hasMore), max 50 trang ────────────────────┐
           │ sync_state → versions                                  │
           │ POST sync-download { session_id, versions, 1000 }      │
           │   server: resolveCaller → whitelist SYNC_TABLES        │
           │           Promise.all fetchChanges(row_version > since)│
           │           scope: ALL / BY_SALESPERSON / BY_ROUTE /     │
           │                  BY_ORDER (orders!inner JOIN)         │
           │           → { tables, has_more, server_time }          │
           │ db.withTransaction { writeTable × N }                  │
           │   decode JsonElement → DTO → Entity → upsert           │
           │   deletedIds → delete (5 bảng)                         │
           │   sync_state.lastVersion = max_version                 │
           └────────────────────────────────────────────────────────┘
        → Completed(totalRows, pages, durationMs)

UPLOAD
  chốt đơn / lưu phiếu / khảo sát  hoặc  startFullSync()
    → SyncManager.startUpload()              [unique work, APPEND_OR_REPLACE]
      → SyncUploadWorker
        → SyncRepositoryImpl.uploadPending()  [uploadMutex, dispatchers.io]
           countOpenVisits() > 0 ? → Skipped (worker coi là success)
           getPending() × 4  (PENDING|FAILED, attempts < 5, limit 50)
           markStatus(SYNCING, sessionId)
           POST sync-upload { session_id, visits, orders, stock_counts, surveys }
             server: INSERT sync_sessions (PK) → trùng thì trả payload_summary cũ
                     visits   → upsert (ép salesperson_id theo JWT)
                     orders   → validateOrder → upsert order+details+promotions
                     stocks   → upsertWithChildren
                     surveys  → upsertWithChildren + survey_photos
                     → { accepted, rejected[{id,entity,code,message}] }
           accepted           → markSynced
           rejected           → sync_errors + markFailed  (KHÔNG retry)
           không nằm ở đâu cả → về PENDING
           lỗi mạng/5xx/rỗng  → revertToPending toàn bộ + Failed(retryable)
```

---

## 6. Điểm cần chú ý khi sửa

1. **`pageSize` không được vượt 1000** — server cắt trần, nhưng nếu ai đó bỏ `Math.min` thì `has_more` sai và client mất dữ liệu âm thầm.
2. **Thứ tự upload → download trong `startFullSync()`** không được đổi, nếu không sẽ có `PRICE_MISMATCH` giả.
3. **`adminClient()` bỏ qua RLS** — mọi query mới trong Edge Function phải tự lọc theo `caller.salespersonId`.
4. **Luôn ép trường chủ sở hữu theo JWT** khi upsert dữ liệu client gửi lên, đừng tin payload.
5. **Bảng mới**: thêm vào `SYNC_TABLES` (server) + `SyncTables` + nhánh `when` trong `writeTable` (client) + `fn_setup_sync_table()` (migration). Thiếu bước migration thì bảng không có `row_version` và query `.gt('row_version', …)` sẽ lỗi.
6. **Mọi đường thoát của `uploadPending()` phải qua `revertToPending`** — bỏ sót một nhánh là bản ghi kẹt `SYNCING` vĩnh viễn, không outbox nào lấy lại được (query chỉ nhận `PENDING`/`FAILED`).
7. **`Skipped` phải map sang `Result.success()`** ở worker, không thì chuỗi full sync đứt ở bước upload và không bao giờ tải xuống.
8. Bảng lạ trong response download bị bỏ qua **và không cập nhật `sync_state`**, nên nó sẽ được gửi lại mãi. Nếu server thêm bảng lớn trước khi client kịp cập nhật, để ý `MAX_PAGES`.
