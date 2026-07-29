<#
.SYNOPSIS
  Smoke test backend: đăng nhập -> sync-download -> kiểm tra dữ liệu trả về.

.DESCRIPTION
  Chạy ở terminal thường (không qua khung chat) vì script hỏi mật khẩu.
  Xác nhận đủ 4 thứ trước khi viết code Android:
    1. User đăng nhập được (đã Auto Confirm chưa)
    2. User đã nối với salespersons (RLS có hoạt động không)
    3. Edge Function chạy được và trả dữ liệu
    4. Delta sync đúng — lần gọi thứ hai không trả lại dữ liệu cũ

.EXAMPLE
  .\scripts\smoke-test.ps1
#>

param(
    [string]$ProjectRef     = 'kvlzyuhvhwzmdvocyhnr',
    [string]$PublishableKey = 'sb_publishable_t1Cfmk-xT53Mk7030a-DWQ_d-BeWIIu',
    [string]$Email          = 'sales01@demo.local'
)

$ErrorActionPreference = 'Stop'
$base = "https://$ProjectRef.supabase.co"

$secure = Read-Host "Mat khau cua $Email" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

<#
  PowerShell 5.1 thường để $_.ErrorDetails.Message rỗng với lỗi HTTP, khiến
  thông báo mất sạch nội dung. Phải tự đọc response stream mới lấy được body
  thật mà server trả về.
#>
function Get-ErrorBody($ErrorRecord) {
    if ($ErrorRecord.ErrorDetails -and $ErrorRecord.ErrorDetails.Message) {
        return $ErrorRecord.ErrorDetails.Message
    }
    $resp = $ErrorRecord.Exception.Response
    if ($null -eq $resp) { return $ErrorRecord.Exception.Message }
    try {
        $stream = $resp.GetResponseStream()
        $stream.Position = 0
        return (New-Object IO.StreamReader($stream)).ReadToEnd()
    } catch {
        return "HTTP $([int]$resp.StatusCode) $($resp.StatusDescription)"
    }
}

# ── 1. Đăng nhập ────────────────────────────────────────────────────────────
Write-Host "`n[1/4] Dang nhap..." -ForegroundColor Cyan
try {
    $auth = Invoke-RestMethod -Method Post `
        -Uri "$base/auth/v1/token?grant_type=password" `
        -Headers @{ apikey = $PublishableKey } `
        -ContentType 'application/json' `
        -Body (@{ email = $Email; password = $password } | ConvertTo-Json)
} catch {
    Write-Host "  THAT BAI: $(Get-ErrorBody $_)" -ForegroundColor Red
    Write-Host "  -> Neu la 'email_not_confirmed': vao Authentication > Users, xoa user" -ForegroundColor Yellow
    Write-Host "     roi tao lai va BAT 'Auto Confirm User'." -ForegroundColor Yellow
    exit 1
}
$token = $auth.access_token
Write-Host "  OK - user_id = $($auth.user.id)" -ForegroundColor Green

$headers = @{
    apikey        = $PublishableKey
    Authorization = "Bearer $token"
}

# ── 2. Sync đầy đủ ──────────────────────────────────────────────────────────
# PostgREST chặn 1000 dòng mỗi request, nên phải LẶP tới khi has_more = false —
# đúng như SyncWorker trên Android sẽ làm.
Write-Host "`n[2/4] sync-download - lap toi khi tai het..." -ForegroundColor Cyan

$versions  = @{}
$rowCounts = @{}
$page      = 0

do {
    $page++
    $body = @{
        session_id = [guid]::NewGuid().ToString()
        versions   = $versions
        page_size  = 1000
    } | ConvertTo-Json

    try {
        $r = Invoke-RestMethod -Method Post -Uri "$base/functions/v1/sync-download" `
            -Headers $headers -ContentType 'application/json' -Body $body
    } catch {
        Write-Host "  THAT BAI: $(Get-ErrorBody $_)" -ForegroundColor Red
        Write-Host "  -> Neu la 'NO_SALESPERSON': chua noi user voi nhan vien. Chay trong SQL Editor:" -ForegroundColor Yellow
        Write-Host "     UPDATE salespersons SET user_id = (SELECT id FROM auth.users" -ForegroundColor Yellow
        Write-Host "     WHERE email = '$Email') WHERE code = 'NV001';" -ForegroundColor Yellow
        exit 1
    }

    $pageRows = 0
    foreach ($p in $r.tables.PSObject.Properties) {
        $n = $p.Value.rows.Count
        $pageRows += $n
        $rowCounts[$p.Name] = ($rowCounts[$p.Name] | ForEach-Object { $_ }) + $n
        $versions[$p.Name]  = $p.Value.max_version
    }
    "    trang {0}: {1,6} dong   (has_more = {2})" -f $page, $pageRows, $r.has_more | Write-Host

    if ($page -ge 20) {
        Write-Host "  DUNG - qua 20 trang, nghi ngo vong lap vo han." -ForegroundColor Red
        exit 1
    }
} while ($r.has_more)

$total = 0
Write-Host "  Tong hop theo bang:" -ForegroundColor Green
foreach ($k in $rowCounts.Keys | Sort-Object) {
    $total += $rowCounts[$k]
    "    {0,-22} {1,6} dong" -f $k, $rowCounts[$k] | Write-Host
}
Write-Host "  Tong: $total dong / $($rowCounts.Count) bang / $page lan goi" -ForegroundColor Green

# ── 3. Gọi lại sau khi đã tải hết — phải rỗng ───────────────────────────────
# Đây mới là phép thử thật cho delta sync: đã tải hết rồi thì lần sau không
# được trả lại gì. Nếu vẫn có dữ liệu, nhân viên sẽ tải lại toàn bộ danh mục
# mỗi lần sync — đúng thứ cơ chế này sinh ra để tránh.
Write-Host "`n[3/4] Goi lai sau khi da tai het (phai rong)..." -ForegroundColor Cyan
$body2 = @{
    session_id = [guid]::NewGuid().ToString()
    versions   = $versions
    page_size  = 1000
} | ConvertTo-Json

$r2 = Invoke-RestMethod -Method Post -Uri "$base/functions/v1/sync-download" `
    -Headers $headers -ContentType 'application/json' -Body $body2

$n2 = ($r2.tables.PSObject.Properties | Measure-Object).Count
if ($n2 -eq 0) {
    Write-Host "  OK - khong co thay doi. Delta sync hoat dong dung." -ForegroundColor Green
} else {
    Write-Host "  CANH BAO - van tra ve $n2 bang. Delta sync co van de." -ForegroundColor Yellow
    $r2.tables.PSObject.Properties | ForEach-Object { "    $($_.Name): $($_.Value.rows.Count)" }
}

# ── 4. Kiểm tra RLS ─────────────────────────────────────────────────────────
Write-Host "`n[4/4] Kiem tra RLS chan truy cap khong xac thuc..." -ForegroundColor Cyan
$anon = Invoke-RestMethod -Method Get -Uri "$base/rest/v1/customers?select=code&limit=5" `
    -Headers @{ apikey = $PublishableKey }
if ($anon.Count -eq 0) {
    Write-Host "  OK - request khong co token khong doc duoc gi." -ForegroundColor Green
} else {
    Write-Host "  CANH BAO - doc duoc $($anon.Count) dong ma khong can dang nhap!" -ForegroundColor Red
}

Write-Host "`nHoan tat.`n" -ForegroundColor Cyan
