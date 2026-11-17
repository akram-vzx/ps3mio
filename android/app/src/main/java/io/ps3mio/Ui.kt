package io.ps3mio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView

/** Stremio (webOS) colour palette. No animations anywhere in the UI. */
object Col {
    val bg = Color.parseColor("#0C0B11")
    val panel = Color.parseColor("#15141B")
    val card = Color.parseColor("#232230")
    val accent = Color.parseColor("#7B5BF5")
    val text = Color.WHITE
    val dim = Color.parseColor("#99FFFFFF")
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** Background with a purple border when focused (D-pad / gamepad navigation). */
fun focusBg(ctx: Context, radius: Int = 8, base: Int = Color.TRANSPARENT): StateListDrawable {
    val focused = GradientDrawable()
    focused.setColor(Col.card)
    focused.cornerRadius = ctx.dp(radius).toFloat()
    focused.setStroke(ctx.dp(3), Col.accent)
    val normal = GradientDrawable()
    normal.setColor(base)
    normal.cornerRadius = ctx.dp(radius).toFloat()
    val sl = StateListDrawable()
    sl.addState(intArrayOf(android.R.attr.state_focused), focused)
    sl.addState(intArrayOf(), normal)
    return sl
}

fun label(ctx: Context, text: String, sizeSp: Float = 14f, color: Int = Col.text, bold: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    t.setTextColor(color)
    if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun button(ctx: Context, text: String, onClick: () -> Unit): TextView {
    val b = label(ctx, text, 15f)
    b.setPadding(ctx.dp(18), ctx.dp(10), ctx.dp(18), ctx.dp(10))
    b.gravity = Gravity.CENTER
    b.isFocusable = true
    b.isClickable = true
    b.background = focusBg(ctx, 8, Col.card)
    b.setOnClickListener { onClick() }
    return b
}
