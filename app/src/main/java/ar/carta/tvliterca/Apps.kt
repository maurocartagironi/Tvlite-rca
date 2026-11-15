package ar.carta.tvliterca

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils

/**
 * Una tarjeta de la grilla: lo justo para dibujarla y saber que hacer con ella.
 *
 * [componente] es null en las tarjetas propias (Ajustes, Liberar memoria...),
 * que no abren otra app sino que hacen algo aca.
 */
class App(
    val label: String,
    val art: Bitmap,
    val componente: ComponentName? = null,
) {
    val packageName: String? get() = componente?.packageName
}

/**
 * Lee las apps que el televisor sabe abrir.
 *
 * Cambia respecto de TV Lite en una cosa importante: el RCA no es Android TV.
 * Casi ninguna app trae banner ni se registra como LEANBACK_LAUNCHER, asi que
 * se piden las dos categorias. Las de Android TV van primero para quedarse
 * con su banner cuando una app aparece en ambas.
 *
 * Lo que no cambia es lo que define la memoria: cada dibujo se rasteriza al
 * tamano exacto de la tarjeta y se guarda como Bitmap chico. Un Drawable de
 * otro paquete arrastra su mapa de bits al tamano original.
 */
object Apps {

    fun load(context: Context, ancho: Int, alto: Int, ocultas: Set<String>): List<App> {
        val pm = context.packageManager
        val propio = context.packageName

        return (consultar(pm, Intent.CATEGORY_LEANBACK_LAUNCHER) + consultar(pm, Intent.CATEGORY_LAUNCHER))
            .asSequence()
            .filter { it.activityInfo.packageName != propio }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in ocultas }
            .mapNotNull { info -> runCatching { convertir(pm, info, ancho, alto) }.getOrNull() }
            // Primero las elegidas a mano, despues el resto alfabetico.
            .sortedWith(compareBy({ orden(it.packageName) }, { it.label.lowercase() }))
            .toList()
    }

    private fun consultar(pm: PackageManager, categoria: String): List<ResolveInfo> =
        runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(categoria), 0)
        }.getOrDefault(emptyList())

    private fun convertir(pm: PackageManager, info: ResolveInfo, ancho: Int, alto: Int): App? {
        val actividad = info.activityInfo
        val label = info.loadLabel(pm).toString()
        // loadBanner existe desde Android 4.4W (API 20).
        val banner = if (Build.VERSION.SDK_INT >= 20) {
            runCatching { actividad.loadBanner(pm) }.getOrNull()
        } else null
        val art = if (banner != null && !esCuadrado(banner)) {
            rasterizarBanner(banner, ancho, alto)
        } else {
            val icono = runCatching { info.loadIcon(pm) }.getOrNull() ?: return null
            rasterizarIcono(icono, label, ancho, alto)
        }
        return App(label, art, ComponentName(actividad.packageName, actividad.name))
    }

    /**
     * Las que van al principio, en este orden. Son las de uso mas comun en
     * un televisor; si no estan instaladas simplemente no aparecen.
     */
    private val PRIORIDAD = listOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.teamsmart.videomanager.tv",
        "com.netflix.ninja",
        "com.netflix.mediaclient",
        "org.xbmc.kodi",
        "com.android.chrome",
    )

    private fun orden(packageName: String?): Int =
        PRIORIDAD.indexOf(packageName).takeIf { it >= 0 } ?: PRIORIDAD.size

    private fun esCuadrado(d: Drawable): Boolean {
        val w = d.intrinsicWidth
        val h = d.intrinsicHeight
        return w > 0 && h > 0 && h.toFloat() / w > 0.75f
    }

    /*
     * RGB_565 y no ARGB_8888: la mitad de bytes. La tarjeta se pinta entera
     * sobre un fondo opaco y las esquinas redondeadas las pone Tarjeta al
     * dibujar, asi que no hay transparencia que conservar.
     */
    private fun lienzo(ancho: Int, alto: Int): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(FONDO_TARJETA)
        return bitmap to canvas
    }

    private fun rasterizarBanner(d: Drawable, ancho: Int, alto: Int): Bitmap {
        val (bitmap, canvas) = lienzo(ancho, alto)
        d.setBounds(0, 0, ancho, alto)
        d.draw(canvas)
        return bitmap
    }

    /**
     * Las apps comunes solo traen un icono cuadrado, y un icono suelto en una
     * tarjeta no alcanza para saber que es. Se dibuja arriba y el nombre
     * debajo, en el mismo bitmap: sigue siendo una sola View por tarjeta.
     */
    private fun rasterizarIcono(d: Drawable, label: String, ancho: Int, alto: Int): Bitmap {
        val (bitmap, canvas) = lienzo(ancho, alto)
        val lado = (alto * 0.50f).toInt()
        val x = (ancho - lado) / 2
        val y = (alto * 0.12f).toInt()
        d.setBounds(x, y, x + lado, y + lado)
        d.draw(canvas)
        escribir(canvas, label, ancho, alto * 0.88f, alto * 0.15f)
        return bitmap
    }

    /** Una tarjeta propia, sin app detras: solo un texto centrado. */
    fun tarjetaTexto(titulo: String, ancho: Int, alto: Int): Bitmap {
        val (bitmap, canvas) = lienzo(ancho, alto)
        escribir(canvas, titulo, ancho, alto * 0.58f, alto * 0.19f)
        return bitmap
    }

    private fun escribir(canvas: Canvas, texto: String, ancho: Int, base: Float, tamano: Float) {
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = tamano
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val corto = TextUtils.ellipsize(texto, android.text.TextPaint(pincel), ancho * 0.9f, TextUtils.TruncateAt.END)
        canvas.drawText(corto.toString(), ancho / 2f, base, pincel)
    }

    /** El intent para abrir la actividad exacta que el sistema nos dio. */
    fun launchIntent(app: App): Intent? {
        val componente = app.componente ?: return null
        return Intent(Intent.ACTION_MAIN)
            .setComponent(componente)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private const val FONDO_TARJETA = 0xFF1C2329.toInt()
}
