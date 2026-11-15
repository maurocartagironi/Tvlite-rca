package ar.carta.tvliterca

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * La tarjeta de una app: una sola View que se dibuja entera. Igual que en
 * TV Lite: cada View anidada es una medida y un dibujo mas por cuadro.
 */
@SuppressLint("ViewConstructor")
class Tarjeta(context: Context, val app: App) : View(context) {

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(app.art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = GROSOR
    }
    private val caja = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        isLongClickable = true
    }

    override fun onFocusChanged(ganado: Boolean, direccion: Int, previo: Rect?) {
        super.onFocusChanged(ganado, direccion, previo)
        // Sin animacion: en un chip como el MT5655 un agrandado animado se ve
        // a saltos y suma trabajo en cada movimiento. Cambio directo de escala.
        val escala = if (ganado) FOCO else 1f
        scaleX = escala
        scaleY = escala
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        caja.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(caja, RADIO, RADIO, pincel)
        if (isFocused) {
            val m = GROSOR / 2f
            caja.set(m, m, width - m, height - m)
            canvas.drawRoundRect(caja, RADIO, RADIO, borde)
        }
    }

    private companion object {
        const val FOCO = 1.08f
        const val RADIO = 10f
        const val GROSOR = 4f
    }
}
