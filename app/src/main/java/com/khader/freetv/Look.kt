package com.khader.freetv

import android.content.Context
import android.graphics.drawable.GradientDrawable

data class Look(
    val name: String,
    val bg: Int,
    val scrim: Int,
    val card: Int,
    val accent: Int,
    val title: Int,
    val sub: Int
)

fun Int.alpha(a: Int): Int = (this and 0x00FFFFFF) or (a shl 24)

object Looks {
    private const val WHITE = 0xFFFFFFFF.toInt()
    val all = listOf(
        Look("⚫🔴 أسود وأحمر", R.drawable.bg_black_red, 0x99000000.toInt(), 0xB3181A20.toInt(),
            0xFFE5182B.toInt(), WHITE, 0xFFCCCCCC.toInt()),
        Look("🌌 ليل نيون", R.drawable.bg_neon_rays, 0x66000000, 0xB3141420.toInt(),
            0xFFFF2D2D.toInt(), WHITE, 0xFFCCCCDD.toInt()),
        Look("🔥 حمم برتقالية", R.drawable.bg_lava, 0x77000000, 0xB3171717.toInt(),
            0xFFFF8A00.toInt(), WHITE, 0xFFDDDDDD.toInt()),
        Look("🧊 ألواح ملونة", R.drawable.bg_tiles, 0x00000000, 0xE61B1E26.toInt(),
            0xFF7C5CFF.toInt(), 0xFF1B1D2A.toInt(), 0xFF333344.toInt())
    )
}

object Ui {
    fun dp(ctx: Context, v: Int): Float = v * ctx.resources.displayMetrics.density

    fun card(ctx: Context, fill: Int, stroke: Int, radiusDp: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, radiusDp)
            setStroke(dp(ctx, 1).toInt().coerceAtLeast(1), stroke)
        }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** internal | mx | vlc | other | ask ; null = not chosen yet (first launch) */
    var player: String?
        get() = sp.getString("player", null)
        set(v) { sp.edit().putString("player", v).apply() }

    var look: Int
        get() = sp.getInt("look", 0).coerceIn(0, Looks.all.size - 1)
        set(v) { sp.edit().putInt("look", v).apply() }
}
