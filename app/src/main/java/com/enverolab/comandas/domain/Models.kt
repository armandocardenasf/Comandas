package com.enverolab.comandas.domain

import java.math.BigDecimal
import java.math.RoundingMode

typealias Row = Map<String, Any?>

fun Row.text(key: String) = this[key]?.toString().orEmpty()

fun Row.id(key: String = "id") = (this[key] as? Number)?.toLong() ?: text(key).toLongOrNull() ?: 0L

fun Row.decimal(key: String) = this[key]?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

fun money(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP)

fun amount(value: String): BigDecimal =
    value.trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Importe inválido: $value")

fun currency(value: BigDecimal) = "$" + money(value).toPlainString()

data class Line(
    val id: Long = 0,
    val productId: Long,
    val variantId: Long? = null,
    val code: String,
    val name: String,
    val description: String = "",
    val quantity: BigDecimal = BigDecimal.ONE,
    val price: BigDecimal,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
) {
    val net: BigDecimal
        get() = money((price * quantity - discountAmount).max(BigDecimal.ZERO))
}

data class Draft(
    val id: Long? = null,
    val tableId: Long,
    val persons: Int,
    val clientId: Long?,
    val discountType: String = "%",
    val discount: BigDecimal = BigDecimal.ZERO,
    val notes: String = "",
    val lines: List<Line>,
)

data class Payment(
    val methodId: Long,
    val methodName: String,
    val received: BigDecimal,
    val factor: BigDecimal,
    val tip: BigDecimal = BigDecimal.ZERO,
    val currency: String = "MXN",
    val person: String = "",
    val assignments: Map<Long, BigDecimal> = emptyMap(),
) {
    val pesos
        get() = money(received * factor)

    val tipPesos
        get() = money(tip * factor)

    val complimentary
        get() = methodName.lowercase().contains("gratis")
}

object Totals {
    fun total(lines: List<Line>, type: String, discount: BigDecimal): BigDecimal =
        total(lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.net }, type, discount)

    fun total(subtotal: BigDecimal, type: String, discount: BigDecimal): BigDecimal {
        require(discount >= BigDecimal.ZERO) { "Descuento negativo" }
        val reduction =
            if (type == "%") subtotal * discount.min(BigDecimal(100)) / BigDecimal(100)
            else discount
        return money((subtotal - reduction).max(BigDecimal.ZERO))
    }

    fun split(total: BigDecimal, people: Int): List<BigDecimal> {
        require(people in 1..100) { "Número de personas inválido" }
        val cents = money(total).movePointRight(2).longValueExact()
        return List(people) {
            BigDecimal.valueOf(cents / people + if (it.toLong() < cents % people) 1 else 0, 2)
        }
    }

    fun change(total: BigDecimal, payments: List<Payment>) =
        money((payments.fold(BigDecimal.ZERO) { a, p -> a + p.pesos } - total).max(BigDecimal.ZERO))
}

/** Quantity buttons preserve the per-unit discount, matching the original POS. */
fun Line.withQuantity(value: BigDecimal): Line {
    require(value > BigDecimal.ZERO && quantity > BigDecimal.ZERO)
    return copy(
        quantity = value,
        discountAmount =
            money(discountAmount.multiply(value).divide(quantity, 8, RoundingMode.HALF_UP)),
    )
}

/** El sugerido cubre el saldo en MXN sin introducir descuentos por redondeo cambiario. */
object TenderAmounts {
    fun suggested(pesos: BigDecimal, factor: BigDecimal): BigDecimal {
        require(pesos.signum() >= 0) { "Saldo inválido" }
        require(factor.signum() > 0) { "Configure un factor de pago mayor que cero" }
        return pesos.divide(factor, 2, RoundingMode.CEILING)
    }
}
