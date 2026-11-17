package com.sudosoc.sudoplay.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Real vibration through the system Vibrator (not Compose's haptic feedback, which many phones ignore).
 * Intensity: 0 off, 1 light, 2 medium, 3 strong.
 */
class Haptics(ctx: Context, private val level: () -> Int) {
    private val vib: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }.getOrNull()

    // [level-1][kind] = (duration ms, amplitude 1..255); kinds: 0 tick, 1 click, 2 heavy
    private val table = arrayOf(
        arrayOf(10L to 55, 14L to 85, 24L to 130),
        arrayOf(14L to 110, 20L to 165, 34L to 215),
        arrayOf(22L to 190, 30L to 255, 48L to 255),
    )

    val available: Boolean get() = vib?.hasVibrator() == true

    fun tick() = fire(0)
    fun click() = fire(1)
    fun heavy() = fire(2)

    /** Plays a click regardless of the setting's "off" state, for the Test button. */
    fun test(lv: Int) = fire(1, lv.coerceIn(1, 3))

    private fun fire(kind: Int, forced: Int = 0) {
        val lv = if (forced > 0) forced else level()
        if (lv <= 0) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        val (ms, amp) = table[(lv - 1).coerceIn(0, 2)][kind]
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, if (v.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        }
    }
}
