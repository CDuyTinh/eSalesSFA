package com.tinhcd.esalessfa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity duy nhất, chứa NavHostFragment. Mọi màn hình là Fragment trong
 * nav_graph — điều hướng tập trung một chỗ thay vì rải Intent khắp nơi.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar trong suốt, icon trắng: nội dung màn hình vẽ tràn lên phía
        // sau nó và phần đầu gần như màn nào cũng là dải đỏ.
        //
        // Phải khai bằng SystemBarStyle.dark chứ không thể trông vào
        // windowLightStatusBar của theme: enableEdgeToEdge() chạy sau theme, và
        // kiểu mặc định auto() chọn màu icon theo chế độ sáng/tối của MÁY, trong
        // khi app luôn dùng theme sáng.
        //
        // Navigation bar thì ngược lại — đáy màn hình là thanh tab trắng nên icon
        // phải tối. Tham số thứ hai là nền mờ dùng cho API 26, nơi icon tối trên
        // navigation bar chưa được hỗ trợ.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, NAV_BAR_SCRIM),
        )
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Chỉ chừa chỗ hai bên và dưới. KHÔNG chừa ở trên: mỗi màn tự đẩy
            // phần đầu của nó xuống bằng padTopForStatusBar(), nhờ vậy nền của
            // màn vẫn trải kín lên sau status bar thay vì để lộ một dải trống.
            v.setPadding(bars.left, 0, bars.right, bars.bottom)

            // Chuyển tiếp cho các màn con DUY NHẤT inset trên. Nếu để inset đáy
            // đi tiếp, những view tự xử lý inset — BottomNavigationView chẳng hạn
            // — sẽ cộng thêm padding đáy lần thứ hai và tự bóp nội dung của mình.
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, bars.top, 0, 0))
                .build()
        }

        // Màu icon status bar đặt theo màn hình đang mở: Login và Sync có phần đầu
        // sáng màu nên icon phải tối, còn lại là dải đỏ nên icon trắng. Đặt tập
        // trung ở đây vì đây là thuộc tính của window: để từng màn tự đổi thì màn
        // kế tiếp phải nhớ đổi lại, thiếu một chỗ là đồng hồ biến mất.
        //
        // Lấy NavController qua NavHostFragment, KHÔNG qua findNavController(id):
        // hàm đó đọc controller từ tag của view, mà view của NavHostFragment chưa
        // được tạo lúc onCreate — activity sẽ chết ngay khi mở.
        val navHost = supportFragmentManager.findFragmentById(R.id.main) as NavHostFragment
        navHost.navController.addOnDestinationChangedListener { _, destination, _ ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = destination.id in LIGHT_TOP_DESTINATIONS
        }
    }

    private companion object {
        val NAV_BAR_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

        val LIGHT_TOP_DESTINATIONS = setOf(R.id.loginFragment, R.id.syncFragment)
    }
}
