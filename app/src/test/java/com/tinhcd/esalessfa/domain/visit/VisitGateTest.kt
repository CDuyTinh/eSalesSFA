package com.tinhcd.esalessfa.domain.visit

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.repository.ActiveVisit
import com.tinhcd.esalessfa.domain.repository.VisitGate
import org.junit.Test

/**
 * Quy tắc chuyển trạng thái cổng nghiệp vụ.
 *
 * Tách thành hàm thuần rồi test riêng vì đây từng là chỗ sinh lỗi: bản đầu chỉ
 * chặn "đang ghé cửa hàng khác" mà quên "đang ghé chính cửa hàng này", nên
 * check-in được hai lần liên tiếp tại cùng một điểm.
 */
class VisitGateTest {

    private val here = "KH001"
    private val elsewhere = "KH999"

    private fun visit(customerId: String) =
        ActiveVisit("v1", customerId, "Cửa hàng $customerId", 1_000_000L)

    private fun gateFor(active: ActiveVisit?): VisitGate = when {
        active == null -> VisitGate.CanCheckIn
        active.customerId == here -> VisitGate.CheckedInHere(active)
        else -> VisitGate.BlockedByOther(active)
    }

    @Test
    fun `chua ghe dau thi duoc check-in`() {
        val gate = gateFor(null)

        assertThat(gate).isEqualTo(VisitGate.CanCheckIn)
        assertThat(gate.canCheckIn()).isTrue()
        assertThat(gate.canOpenVisitScreen()).isTrue()
        assertThat(gate.requiresCustomerLocation()).isTrue()
        assertThat(gate.canDoBusiness()).isFalse()
    }

    @Test
    fun `dang ghe chinh cua hang nay thi mo thao tac nhung KHONG check-in lai`() {
        val gate = gateFor(visit(here))

        assertThat(gate).isInstanceOf(VisitGate.CheckedInHere::class.java)
        assertThat(gate.canDoBusiness()).isTrue()
        // Đây là lỗi cũ: cho phép check-in lần hai và sinh ra lượt ghé trùng.
        assertThat(gate.canCheckIn()).isFalse()
    }

    @Test
    fun `dang ghe chinh cua hang nay VAN phai vao duoc man vieng tham de check-out`() {
        val gate = gateFor(visit(here))

        // Lỗi từng gặp: khoá cửa vào màn viếng thăm khi đang ghé, khiến không
        // còn đường nào để check-out.
        assertThat(gate.canOpenVisitScreen()).isTrue()
        // Check-out không xác thực bán kính nên không đòi toạ độ khách hàng.
        assertThat(gate.requiresCustomerLocation()).isFalse()
    }

    @Test
    fun `dang ghe cua hang khac thi chan tat ca ke ca vao man vieng tham`() {
        val gate = gateFor(visit(elsewhere))

        assertThat(gate).isInstanceOf(VisitGate.BlockedByOther::class.java)
        assertThat(gate.canCheckIn()).isFalse()
        assertThat(gate.canOpenVisitScreen()).isFalse()
        assertThat(gate.canDoBusiness()).isFalse()
    }

    @Test
    fun `chan nhung mang theo ten cua hang de bao cho nguoi dung`() {
        val gate = gateFor(visit(elsewhere)) as VisitGate.BlockedByOther

        assertThat(gate.visit.customerName).isEqualTo("Cửa hàng KH999")
    }
}
