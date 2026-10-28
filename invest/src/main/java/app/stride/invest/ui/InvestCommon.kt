package app.stride.invest.ui

import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Invest's accent (emerald, like money that grows). */
val InvestAccent = Color(0xFF34D399)
val InvestDown = Color(0xFFF87171)

/** 10250.5 → "10.250,50" (Serbian grouping). */
fun money(v: Double, decimals: Int = 2): String = String.format(Locale.GERMANY, "%,.${decimals}f", v)

/** A change with its sign: "+149,50" / "−20,00". */
fun signed(v: Double, decimals: Int = 2): String = (if (v >= 0) "+" else "−") + money(kotlin.math.abs(v), decimals)

/** Quantity without needless decimals: 80 → "80", 0.5 → "0,5". */
fun qty(v: Double): String = if (v == Math.floor(v)) "%,.0f".format(Locale.GERMANY, v) else String.format(Locale.GERMANY, "%,.4f", v).trimEnd('0').trimEnd(',')

/** Percent with its sign, one decimal: "+12,3%". */
fun pct(v: Double): String = signed(v, 1) + "%"

/** Green for gains, red for losses. */
fun gainColor(v: Double?): Color = if (v == null) app.stride.ui.theme.StrideColors.Dim else if (v >= 0) InvestAccent else InvestDown

/** Slice colors for the mix donut (no pink). */
val SLICE_COLORS = listOf(0xFF34D399, 0xFF60A5FA, 0xFFFBBF24, 0xFFA78BFA, 0xFF22D3EE, 0xFFF97316, 0xFF94A3B8).map { Color(it) }
