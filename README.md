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
| **Promotion engine** | Module **Kotlin thuần, không phụ thuộc Android** · chiết khấu bậc theo SL/giá trị, KM dòng & toàn đơn, hàng tặng, combo bộ, chiết khấu tay, quota · Strategy + Chain of Responsibility · unit test chạy trên JVM |
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

**Luật kiến trúc:** `:feature:*` **không được** phụ thuộc `:data` — chỉ nói chuyện qua `:domain`.
Nhờ vậy khi chuyển UI từ XML sang Jetpack Compose, tầng domain và data không phải sửa một dòng nào.

### Module

```
:app                    Application, Navigation graph gốc, DI root
:domain                 ⭐ Pure Kotlin JVM — business logic, test trên JVM
:data                   RepositoryImpl, mapper, datasource
:core:common            AppResult, DispatcherProvider, extensions
:core:database          Room DB, DAO, Entity, Migration
:core:network           Supabase client, error mapper
:core:datastore         DataStore, SessionManager
:core:ui                BaseFragment, theme, custom view
:core:sync              SyncManager, WorkManager worker, outbox
:feature:auth           Đăng nhập / phiên làm việc
```

---

## Tech stack

| Nhóm | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin 2.3, Coroutines, Flow |
| UI | XML + ViewBinding, Material 3, Navigation Component *(sẽ chuyển sang Jetpack Compose)* |
| Kiến trúc | MVVM + Clean Architecture, multi-module Gradle |
| DI | Hilt |
| Local | Room, Paging 3, DataStore |
| Backend | Supabase — Postgres, Auth, Storage, RLS, Edge Functions, Realtime |
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
# điền sdk.dir, SUPABASE_URL, SUPABASE_ANON_KEY vào local.properties

./gradlew :app:assembleDebug     # build
./gradlew :domain:test           # chạy unit test business logic
```

---

## Roadmap

- [x] Multi-module skeleton, Version Catalog, Hilt + Room + KSP
- [ ] Supabase schema + RLS + seed data
- [ ] Auth + Room entity + Navigation
- [ ] **Sync engine** (download delta + upload outbox)
- [ ] Tuyến viếng thăm + danh sách khách hàng
- [ ] GPS check-in / check-out
- [ ] **Promotion engine** + unit test
- [ ] Take Order
- [ ] Kiểm kê tồn kho + Khảo sát trưng bày + CameraX
- [ ] Dashboard KPI + Báo cáo
- [ ] Chuyển UI sang Jetpack Compose

---

## Tác giả

**Cao Duy Tịnh** — Android Developer
[LinkedIn](https://linkedin.com/in/tinhcao99) · duytinh100599@gmail.com
