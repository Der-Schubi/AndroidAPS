package app.aaps.persistentNotification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat

/**
 * Draws a glucose value as a status bar icon.
 *
 * The status bar tints small icons and only looks at the alpha channel, so the text is drawn in white on a
 * transparent background. The text is scaled to fill the square, so "98" gets a larger font than "10.5".
 *
 * A struck-through value marks stale data. Colours are lost to the tint, but partial transparency survives it,
 * so the line is a half transparent white: it shows as a grey line across the digits, in one piece from edge to
 * edge, and the digits stay readable around it.
 */
internal object BgNotificationIcon {

    private const val SIZE_PX = 96
    private const val STRIKE_ALPHA = 140
    private const val STRIKE_WIDTH = SIZE_PX * 0.08f

    fun create(text: String, strikeThrough: Boolean = false): IconCompat {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            // Fit the width first, then cap the height so short values like "98" do not overflow.
            textSize = SIZE_PX.toFloat()
            val width = measureText(text)
            if (width > SIZE_PX) textSize = textSize * SIZE_PX / width
            textSize = textSize.coerceAtMost(SIZE_PX * 0.8f)
        }
        val baseline = SIZE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, SIZE_PX / 2f, baseline, paint)
        if (strikeThrough) {
            val strike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // SRC replaces the pixels under the line instead of adding to them, so the line is grey over
                // the white digits too. A plain draw over opaque white would stay white.
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                color = Color.argb(STRIKE_ALPHA, 255, 255, 255)
                strokeWidth = STRIKE_WIDTH
            }
            // Digits are about 0.7 em tall, so their middle sits 0.35 em above the baseline.
            val y = baseline - paint.textSize * 0.35f
            canvas.drawLine(0f, y, SIZE_PX.toFloat(), y, strike)
        }
        return IconCompat.createWithBitmap(bitmap)
    }
}
