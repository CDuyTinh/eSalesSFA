package com.tinhcd.esalessfa.domain.model.order

object OrderCalculator {

    fun totals(lines: List<OrderLine>, result: PromotionResult): OrderTotals {
        val subTotal = lines.sumOf { it.grossAmount }
        val discount = result.totalDiscount.coerceAtMost(subTotal)

        val vat = lines.sumOf { line ->
            val lineNet = line.grossAmount - result.discountForLine(line.lineNo)
            MoneyMath.percentOf(lineNet.coerceAtLeast(0), line.vatRate)
        }

        val net = subTotal - discount
        return OrderTotals(
            subTotal = subTotal,
            discountAmount = discount,
            netAmount = net,
            vatAmount = vat,
            totalAmount = net + vat,
        )
    }
}
