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
