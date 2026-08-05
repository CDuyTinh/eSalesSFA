package com.tinhcd.esalessfa.feature.customer.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.tinhcd.esalessfa.BuildConfig
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentCustomerMapBinding
import com.tinhcd.esalessfa.databinding.ItemMapInfoWindowBinding
import com.tinhcd.esalessfa.databinding.ItemMapMarkerBinding
import com.tinhcd.esalessfa.domain.model.customer.RouteCustomer
import com.tinhcd.esalessfa.domain.model.customer.VisitState
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.feature.customer.detail.CustomerDetailFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Bản đồ tuyến hôm nay, theo PrepareMapFragment của bản eSales gốc.
 *
 * Mỗi khách hàng là một ghim mang số thứ tự ghé, màu theo trạng thái. Chạm ghim
 * thì mở bảng thông tin ở đáy màn hình, đồng thời hiện chú thích ngay trên ghim.
 */
@AndroidEntryPoint
class CustomerMapFragment : Fragment(R.layout.fragment_customer_map) {

    private val viewModel: CustomerMapViewModel by viewModels()

    private var map: GoogleMap? = null

    /**
     * Danh sách mới nhất, giữ lại vì bản đồ và dữ liệu sẵn sàng không cùng lúc.
     *
     * Dữ liệu thường về trước khi GoogleMap gọi callback, nên phải nhớ lại để vẽ
     * ngay khi bản đồ sẵn sàng — nếu không màn hình trống cho tới lần cập nhật
     * dữ liệu tiếp theo, mà lần đó có thể không bao giờ tới.
     */
    private var pending: List<RouteCustomer> = emptyList()

    /** Tra ngược từ ghim ra khách hàng khi người dùng chạm. */
    private val markers = mutableMapOf<String, RouteCustomer>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerMapBinding.bind(view)

        view.padTopForStatusBar()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        if (!BuildConfig.HAS_MAPS_KEY) {
            // Không có khoá thì SDK chỉ vẽ ô xám; nói rõ lý do còn hơn để trống.
            binding.mapContainer.visibility = View.GONE
            binding.legend.visibility = View.GONE
            binding.messageText.visibility = View.VISIBLE
            binding.messageText.setText(R.string.customer_map_no_key)
            return
        }

        // Nút "Xem chi tiết" nằm trong bảng thông tin, nhưng điều hướng là việc
        // của màn hình này.
        childFragmentManager.setFragmentResultListener(
            CustomerMapInfoSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            result.getString(CustomerMapInfoSheet.RESULT_CUSTOMER_ID)?.let(::openDetail)
        }

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapContainer) as SupportMapFragment

        mapFragment.getMapAsync { googleMap ->
            map = googleMap
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.setInfoWindowAdapter(InfoWindowAdapter())
            googleMap.setOnMarkerClickListener { marker ->
                markers[marker.id]?.let(::showInfoSheet)
                // false: vẫn để bản đồ mở chú thích và đưa ghim vào giữa màn hình.
                false
            }
            render(binding, pending)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.customers.collect { list ->
                    pending = list
                    render(binding, list)
                }
            }
        }
    }

    private fun render(binding: FragmentCustomerMapBinding, customers: List<RouteCustomer>) {
        val googleMap = map ?: return

        binding.legendCount.text = getString(R.string.customer_map_count, customers.size)
        binding.messageText.visibility = if (customers.isEmpty()) View.VISIBLE else View.GONE
        if (customers.isEmpty()) {
            binding.messageText.setText(R.string.customer_map_no_location)
            return
        }

        googleMap.clear()
        markers.clear()
        val bounds = LatLngBounds.builder()

        customers.forEach { item ->
            val point = item.customer.location ?: return@forEach
            val position = LatLng(point.latitude, point.longitude)
            bounds.include(position)

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(
                        getString(
                            R.string.customer_map_info_title,
                            item.customer.code,
                            item.customer.name,
                        )
                    )
                    .snippet(item.customer.address.orEmpty())
                    .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap(item)))
            )
            marker?.let { markers[it.id] = item }
        }

        moveCamera(customers, bounds.build())
    }

    /**
     * Khung nhìn ôm hết các ghim.
     *
     * Cụm khách hàng nằm sát nhau (dưới 1km) thì newLatLngBounds sẽ phóng tới mức
     * tối đa và người dùng chỉ thấy một mảng xám — trường hợp đó dùng zoom cố
     * định, giống cách bản gốc xử lý trong setZoomToMap.
     */
    private fun moveCamera(customers: List<RouteCustomer>, bounds: LatLngBounds) {
        val googleMap = map ?: return

        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            bounds.northeast.latitude, bounds.northeast.longitude,
            bounds.southwest.latitude, bounds.southwest.longitude,
            results,
        )

        val update = if (customers.size == 1 || results[0] < TIGHT_CLUSTER_METERS) {
            CameraUpdateFactory.newLatLngZoom(bounds.center, DEFAULT_ZOOM)
        } else {
            CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING)
        }
        googleMap.moveCamera(update)
    }

    private fun showInfoSheet(item: RouteCustomer) {
        CustomerMapInfoSheet.newInstance(item).show(childFragmentManager, "customer_map_info")
    }

    /**
     * Dựng icon ghim từ layout: số thứ tự ghé nằm trong giọt nước, thân ghim tô
     * theo trạng thái. GoogleMap chỉ nhận Bitmap nên phải tự đo và vẽ ra.
     */
    private fun markerBitmap(item: RouteCustomer): Bitmap {
        val binding = ItemMapMarkerBinding.inflate(LayoutInflater.from(requireContext()))
        binding.number.text = item.sortOrder.toString()

        val color = ContextCompat.getColor(requireContext(), item.visitState.markerColor())
        binding.pin.drawable.mutate().also { DrawableCompat.setTint(it, color) }

        val root = binding.root
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            root.measuredWidth,
            root.measuredHeight,
            Bitmap.Config.ARGB_8888,
        )
        root.draw(Canvas(bitmap))
        return bitmap
    }

    private fun openDetail(customerId: String) {
        findNavController().navigate(
            R.id.customerDetailFragment,
            bundleOf(CustomerDetailFragment.ARG_CUSTOMER_ID to customerId),
        )
    }

    /** Cùng bộ màu với nhãn trạng thái trong danh sách để hai màn đọc như một. */
    private fun VisitState.markerColor(): Int = when (this) {
        VisitState.NOT_VISITED -> R.color.brandRed
        VisitState.IN_PROGRESS -> R.color.stateBlue
        VisitState.DONE -> R.color.stateGreen
    }

    /**
     * Chú thích nổi trên ghim: mã + tên và địa chỉ.
     *
     * Chỉ thay phần RUỘT (getInfoContents), giữ khung bong bóng mặc định của
     * Google — tự vẽ cả khung thì mất luôn cái đuôi nhọn trỏ xuống ghim.
     */
    private inner class InfoWindowAdapter : GoogleMap.InfoWindowAdapter {

        override fun getInfoWindow(marker: Marker): View? = null

        override fun getInfoContents(marker: Marker): View {
            val binding = ItemMapInfoWindowBinding.inflate(layoutInflater)
            binding.infoTitle.text = marker.title
            binding.infoAddress.text = marker.snippet.orEmpty()
            binding.infoAddress.visibility =
                if (marker.snippet.isNullOrBlank()) View.GONE else View.VISIBLE
            return binding.root
        }
    }

    private companion object {
        const val DEFAULT_ZOOM = 16f
        const val BOUNDS_PADDING = 96
        const val TIGHT_CLUSTER_METERS = 1_000f
    }
}
