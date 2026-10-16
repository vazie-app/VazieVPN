package app.vazie.vpn.core.designsystem.feedback

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** A buzz that actually happens. */
@Immutable
class VazieHaptics internal constructor(
    private val view: View,
    private val vibrator: Vibrator?,
) {

    /** Something the user asked for worked. */
    fun confirm() {
        if (view.performHapticFeedback(CONFIRM)) return
        vibrate(confirmEffect())
    }

    /** Something the user asked for did not work. */
    fun reject() {
        if (view.performHapticFeedback(REJECT)) return
        vibrate(rejectEffect())
    }

    /** The fallback, gated on the one question `performHapticFeedback`'s `false` does not answer. */
    private fun vibrate(effect: VibrationEffect?) {
        if (!hapticFeedbackWanted()) return
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return
        effect?.let(motor::vibrate)
    }

    // Deprecated with no replacement, but still the key the window's haptic callback reads.
    @Suppress("DEPRECATION")
    private fun hapticFeedbackWanted(): Boolean {
        if (!view.isHapticFeedbackEnabled) return false
        return Settings.System.getInt(
            view.context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            HAPTIC_FEEDBACK_DEFAULT_ON,
        ) != 0
    }

    /** One soft click. */
    private fun confirmEffect(): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(CLICK_MILLIS, CLICK_AMPLITUDE)
        }

    /** Two short pulses. */
    private fun rejectEffect(): VibrationEffect =
        VibrationEffect.createWaveform(REJECT_PATTERN, REJECT_AMPLITUDES, NO_REPEAT)

    private companion object {
        /** API 30 constants; on 28-29 `performHapticFeedback` returns false and the fallback runs. */
        const val CONFIRM = HapticFeedbackConstants.CONFIRM
        const val REJECT = HapticFeedbackConstants.REJECT

        /** Absent on a device that has never written the setting; the platform default is on. */
        const val HAPTIC_FEEDBACK_DEFAULT_ON = 1

        const val CLICK_MILLIS = 20L
        const val CLICK_AMPLITUDE = 128
        const val NO_REPEAT = -1

        /** Off, on, off, on — the waveform starts with a wait, by contract. */
        val REJECT_PATTERN = longArrayOf(0, 24, 90, 24)
        val REJECT_AMPLITUDES = intArrayOf(0, 180, 0, 180)
    }
}

/** The haptics for the current composition, resolved once. */
@Composable
fun rememberVazieHaptics(): VazieHaptics {
    val view = LocalView.current
    return remember(view) {
        VazieHaptics(view = view, vibrator = view.context.vibrator())
    }
}

/** The motor, through whichever API this device's version has. */
private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }
