# eSalesSFA

Ứng dụng **SFA (Sales Force Automation)** offline-first cho nhân viên bán hàng thị trường —
viết lại các nghiệp vụ tiêu biểu của một hệ thống DMS doanh nghiệp bằng **Kotlin + MVVM + Clean Architecture**.

> 🚧 **Đang phát triển.** Xem [Roadmap](#roadmap) để biết tiến độ.

---

## Bối cảnh

Nhân viên bán hàng đi tuyến ngoài thị trường, nơi sóng 4G chập chờn hoặc không có.
App phải hoạt động **trọn vẹn khi offline**: xem tuyến, check-in bằng GPS, kiểm kê tồn kho cửa hàng,
đặt hàng có tính khuyến mãi, chụp ảnh trưng bày — rồi **tự đồng bộ** khi có mạng trở lại.

---

## Điểm nhấn kỹ thuật

| | |
|---|---|
| **Sync engine offline-first 2 chiều** | Delta version theo từng bảng · outbox pattern · idempotent bằng session UUID · WorkManager unique work chống sync trùng · ghi batch trong Room transaction |
| **Promotion engine** | **Kotlin thuần, không import `android.*`** · chiết khấu bậc theo SL/giá trị, KM dòng & toàn đơn, hàng tặng, combo bộ, chiết khấu tay, ngân sách, loại trừ chương trình · Strategy + Chain of Responsibility · **37 unit test chạy trên JVM trong 0,02s** · đơn 200 dòng tính lại < 50ms |
| **GPS check-in/out** | FusedLocation bọc thành Flow · xác thực bán kính & độ chính xác · bắt lý do khi vượt khoảng cách · Foreground Service tracking · phát hiện mock location |
| **Media** | CameraX chụp ảnh minh chứng · nén < 300KB + watermark GPS/thời gian · hàng đợi upload offline lên Supabase Storage |

---

## Kiến trúc

```
┌──────────────────────────────────────────────────────┐
│  PRESENTATION  (:feature:*)                          │
│  Fragment + ViewBinding  ←→  ViewModel               │
│  StateFlow<UiState> / SharedFlow<UiEvent>            │
└───────────────────────┬──────────────────────────────┘
                        │ chỉ phụ thuộc xuống
┌───────────────────────▼──────────────────────────────┐
│  DOMAIN  (:domain — PURE KOTLIN, không có Android)   │
│  Entity · UseCase · Repository INTERFACE             │
│  PromotionEngine · GeoUtils · Validators             │
└───────────────────────┬──────────────────────────────┘
                        │ implement bởi
┌───────────────────────▼──────────────────────────────┐
│  DATA  (:data)                                       │
│  RepositoryImpl  →  Room (single source of truth)    │
│                  →  Supabase (remote)                │
└──────────────────────────────────────────────────────┘
```

**Luật kiến trúc:** package `feature` **không được** import từ package `data` — chỉ nói chuyện
qua `domain`. Nhờ vậy khi chuyển UI từ XML sang Jetpack Compose, tầng domain và data không
phải sửa một dòng nào.

### Cấu trúc package

Project là **single-module**; phân tầng giữ bằng package bên trong `:app`.

```
com.tinhcd.esalessfa
├── domain/             ⭐ Business logic thuần — không import android.*
│   ├── model/              Entity nghiệp vụ
│   ├── repository/         Interface (implement ở data/)
│   ├── usecase/
│   ├── promotion/          Engine khuyến mãi — Strategy + Chain of Responsibility
│   └── geo/                Haversine, validate bán kính check-in
├── data/
│   ├── repository/         RepositoryImpl
│   ├── mapper/             DTO ↔ Entity ↔ Domain model
│   └── datasource/
├── core/
│   ├── common/             AppResult, DispatcherProvider, extensions
│   ├── database/           Room DB, DAO, Entity, Migration
│   ├── network/            Supabase Auth/Storage, Retrofit → Edge Functions
│   ├── datastore/          DataStore, SessionManager
│   ├── ui/                 BaseFragment, custom view, binding adapter
│   └── sync/               SyncManager, WorkManager worker, outbox
└── feature/
    ├── auth/               Đăng nhập / phiên làm việc
    ├── home/               Dashboard + tuyến hôm nay
    ├── customer/           Danh sách, chi tiết, sales step, bản đồ
    ├── visit/              Check-in/out, GPS service
    ├── order/              Take order + UI khuyến mãi
    ├── inventory/          Kiểm kê tồn cửa hàng
    ├── survey/             Perfect Store, MSL/OOS, CameraX
    └── report/
```

> Unit test của `domain/` nằm ở `app/src/test` nên vẫn **chạy trên JVM**, không cần emulator.
> Luật "domain không import android.*" giờ là quy ước — không còn được compiler chặn như khi
> tách module riêng.

---

## Tech stack

| Nhóm | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin 2.3, Coroutines, Flow |
| UI | XML + ViewBinding, Material 3, Navigation Component *(sẽ chuyển sang Jetpack Compose)* |
| Kiến trúc | MVVM + Clean Architecture (phân tầng bằng package) |
| DI | Hilt |
| Local | Room, Paging 3, DataStore |
| Backend | Supabase — Postgres, Auth, Storage, RLS, **Edge Functions (Deno)** |
| Gọi API | Retrofit + kotlinx.serialization → `/functions/v1/*` |
| Background | WorkManager, Foreground Service |
| Media / Map | CameraX, Coil, Google Maps, FusedLocation |
| Test | JUnit, Truth, MockK, Turbine, Room in-memory |
| Build | AGP 9 (built-in Kotlin), Gradle Version Catalog, KSP |

---

## Chạy project

**Yêu cầu:** JDK 21 · Android Studio (AGP 9.2+) · Android SDK 37

```bash
git clone https://github.com/CDuyTinh/eSalesSFA.git
cd eSalesSFA
cp local.properties.example local.properties
# điền sdk.dir, SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY vào local.properties

./gradlew :app:assembleDebug       # build
./gradlew :app:testDebugUnitTest   # chạy unit test (JVM, không cần emulator)
```

Backend: xem [`supabase/README.md`](supabase/README.md) (schema + seed) và
[`supabase/functions/README.md`](supabase/functions/README.md) (deploy Edge Functions).

---

## Roadmap

- [x] Khung project, Version Catalog, Hilt + Room + KSP
- [x] Supabase schema (24 bảng) + RLS + seed data
- [x] Edge Functions `sync-download` / `sync-upload`
- [x] Tầng network: Retrofit → Edge Functions, Supabase Auth/Storage
- [x] Room 24 bảng + DAO + outbox
- [x] **Sync engine download** — delta version, phân trang, ghi theo transaction
- [x] Đăng nhập + màn hình đồng bộ + Home
- [x] **Sync engine upload** — outbox → `sync-upload`, idempotent, tự retry
- [x] Danh sách & chi tiết khách hàng (Paging 3, tìm kiếm có debounce)
- [x] **Promotion engine** — 5 loại rule, 37 unit test
- [ ] GPS check-in / check-out
- [x] Take Order — giỏ hàng, đa đơn vị tính, khuyến mãi tính realtime
- [ ] Kiểm kê tồn kho + Khảo sát trưng bày + CameraX
- [ ] Dashboard KPI + Báo cáo
- [ ] Chuyển UI sang Jetpack Compose

### Đã chạy thật

Đăng nhập → tải **5.613 dòng / 17 bảng qua 4 lượt phân trang** → Home hiển thị
200 khách hàng · 300 sản phẩm · 15 chương trình khuyến mãi · 34 khách trong tuyến hôm nay.

---

## Tác giả

**Cao Duy Tịnh** — Android Developer
[LinkedIn](https://linkedin.com/in/tinhcao99) · duytinh100599@gmail.com
