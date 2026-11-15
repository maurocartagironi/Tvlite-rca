package ar.carta.tvliterca

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La pantalla de inicio: reloj, memoria libre y una grilla de apps.
 *
 * Sale de TV Lite. Lo que cambio para el RCA:
 * - grilla en vez de una fila: estos televisores traen muchas apps de
 *   fabrica y una fila de treinta es imposible de recorrer con el control;
 * - sin fotos de fondo ni clima: no hay adb para cargar fotos y el
 *   televisor tiene menos memoria todavia;
 * - todo lo que es de Android nuevo, detras de un chequeo de version;
 * - "Liberar memoria" y ocultar apps, porque el problema de este equipo es
 *   justamente la memoria.
 *
 * Igual que en TV Lite, todo se arma en codigo y no en XML.
 */
class MainActivity : Activity() {

    private lateinit var reloj: TextView
    private lateinit var fecha: TextView
    private lateinit var memoria: TextView
    private lateinit var nombre: TextView
    private lateinit var scroll: ScrollView
    private lateinit var grilla: LinearLayout

    private var apps: List<App> = emptyList()
    private var registrado = false

    private val prefs by lazy { getSharedPreferences("tvlite", Context.MODE_PRIVATE) }

    /** Cambia cada minuto, avisado por el sistema: no hay nada que consultar. */
    private val minuto = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            ponerHora()
            ponerMemoria()
        }
    }

    /** Si instalas o borras una app, la grilla se rehace sola. */
    private val paquetes = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = cargar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Envuelto entero: si la pantalla de inicio no abre, el televisor se
        // queda sin ninguna. Mejor una pantalla fea que lleve a Ajustes.
        runCatching { montar() }.onFailure {
            Log.e(TAG, "no se pudo armar la pantalla", it)
            setContentView(emergencia())
        }
    }

    private fun montar() {
        setContentView(armar())

        registrar(minuto, IntentFilter(Intent.ACTION_TIME_TICK))
        registrar(paquetes, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        registrado = true

        ponerHora()
        cargar()
    }

    /** registerReceiver con flags existe desde Android 8; antes, sin flags. */
    private fun registrar(receptor: BroadcastReceiver, filtro: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receptor, filtro)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (registrado) {
            runCatching { unregisterReceiver(minuto) }
            runCatching { unregisterReceiver(paquetes) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::memoria.isInitialized) {
            ponerHora()
            ponerMemoria()
        }
    }

    /** Apretar Inicio estando ya en el inicio vuelve arriba de todo. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (::scroll.isInitialized) {
            scroll.scrollTo(0, 0)
            primeraTarjeta()?.requestFocus()
        }
    }

    /** Atras en la pantalla de inicio no tiene adonde ir. */
    @Deprecated("Se sigue llamando con targetSdk 36 sin back predictivo")
    override fun onBackPressed() = Unit

    /** La tecla Menu del control abre las opciones de la tarjeta con foco. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val tarjeta = currentFocus as? Tarjeta
            if (tarjeta?.app?.componente != null) {
                opciones(tarjeta.app)
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun emergencia(): View = TextView(this).apply {
        text = "TV Lite no pudo abrir.  ·  Aceptar para ir a Ajustes."
        gravity = Gravity.CENTER
        setTextColor(BLANCO)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setBackgroundColor(0xFF0B0E11.toInt())
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        setOnClickListener { abrirAjustes() }
    }

    // ---------------------------------------------------------------- armado

    private fun armar(): View {
        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(MARGEN_DP), dp(28), dp(MARGEN_DP), 0)
            clipChildren = false
        }

        // Arriba: a la izquierda hora y fecha, a la derecha la memoria libre.
        val cabecera = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val hora = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        reloj = texto(40f, BLANCO)
        fecha = texto(15f, TENUE)
        hora.addView(reloj)
        hora.addView(fecha)
        cabecera.addView(hora, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        memoria = texto(15f, TENUE).apply { gravity = Gravity.END }
        cabecera.addView(memoria)
        columna.addView(cabecera)

        nombre = texto(17f, BLANCO).apply {
            // Alto fijo aunque este vacio, para que nada salte al mover el foco.
            height = dp(30)
            setPadding(0, dp(8), 0, 0)
        }
        columna.addView(nombre)

        grilla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Aire para que la tarjeta con foco se agrande sin recortarse.
            val aire = dp(10)
            setPadding(aire, aire, aire, dp(24))
            clipChildren = false
            clipToPadding = false
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(grilla, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        columna.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return columna
    }

    private fun texto(sp: Float, color: Int) = TextView(this).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setSingleLine(true)
    }

    // --------------------------------------------------------------- contenido

    private fun cargar() {
        val ancho = dp(ANCHO_DP)
        val alto = dp(ALTO_DP)
        val ocultas = ocultas()
        // Fuera del hilo principal: consultar el sistema y rasterizar decenas
        // de dibujos no es gratis, y trabar el arranque es lo que vinimos a evitar.
        Thread {
            val lista = runCatching { Apps.load(this, ancho, alto, ocultas) }
                .onFailure { Log.w(TAG, "no se pudo leer la lista de apps", it) }
                .getOrDefault(emptyList())
            val propias = propias(ancho, alto, ocultas.isNotEmpty())
            runOnUiThread { if (!isFinishing) mostrar(lista, propias, ancho, alto) }
        }.start()
    }

    /**
     * Las tarjetas propias van al final. "Ajustes" esta siempre, aunque falle
     * todo lo demas: es la salida para volver al launcher de fabrica sin
     * computadora.
     */
    private fun propias(ancho: Int, alto: Int, hayOcultas: Boolean): List<Pair<App, () -> Unit>> {
        val lista = mutableListOf<Pair<App, () -> Unit>>()
        lista += App("Liberar memoria", Apps.tarjetaTexto("Liberar memoria", ancho, alto)) to ::liberar
        if (hayOcultas) {
            lista += App("Mostrar ocultas", Apps.tarjetaTexto("Mostrar ocultas", ancho, alto)) to ::mostrarOcultas
        }
        lista += App("Ajustes del televisor", Apps.tarjetaTexto("Ajustes", ancho, alto)) to ::abrirAjustes
        return lista
    }

    private fun mostrar(lista: List<App>, propias: List<Pair<App, () -> Unit>>, ancho: Int, alto: Int) {
        // Si la tarjeta con foco sigue existiendo, el foco se queda ahi.
        val antes = (currentFocus as? Tarjeta)?.app?.packageName
        apps = lista
        grilla.removeAllViews()

        val separacion = dp(SEPARACION_DP)
        val disponible = resources.displayMetrics.widthPixels - 2 * dp(MARGEN_DP) - 2 * dp(10)
        val columnas = maxOf(1, (disponible + separacion) / (ancho + separacion))

        val tarjetas = mutableListOf<Tarjeta>()
        lista.forEach { app ->
            tarjetas += Tarjeta(this, app).apply {
                setOnClickListener { abrir(app) }
                setOnLongClickListener { opciones(app); true }
            }
        }
        propias.forEach { (app, accion) ->
            tarjetas += Tarjeta(this, app).apply { setOnClickListener { accion() } }
        }

        var fila: LinearLayout? = null
        tarjetas.forEachIndexed { i, tarjeta ->
            if (i % columnas == 0) {
                fila = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                }
                grilla.addView(fila, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    bottomMargin = separacion
                })
            }
            tarjeta.setOnFocusChangeListener { _, tiene -> if (tiene) nombre.text = tarjeta.app.label }
            fila!!.addView(tarjeta, LinearLayout.LayoutParams(ancho, alto).apply {
                if (i % columnas != columnas - 1) rightMargin = separacion
            })
        }

        if (lista.isEmpty()) nombre.text = "No se pudo leer la lista de apps"
        val enfocar = tarjetas.firstOrNull { antes != null && it.app.packageName == antes }
            ?: tarjetas.firstOrNull()
        enfocar?.requestFocus()
    }

    private fun primeraTarjeta(): Tarjeta? =
        ((grilla.getChildAt(0) as? ViewGroup)?.getChildAt(0)) as? Tarjeta

    private fun abrir(app: App) {
        val intent = Apps.launchIntent(app)
        if (intent == null || runCatching { startActivity(intent) }.isFailure) {
            // Quedo una app que el sistema ya no sabe abrir: rehacer la lista la saca.
            cargar()
        }
    }

    // --------------------------------------------------------------- opciones

    /**
     * Mantener apretado OK (o la tecla Menu) sobre una app. "Informacion" lleva
     * a la pantalla del sistema desde donde se borra la cache, se fuerza la
     * detencion o se desinstala: es lo que mas sirve para que el televisor
     * ande mejor, y asi se llega en dos clics.
     */
    private fun opciones(app: App) {
        val paquete = app.packageName ?: return
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Ocultar de esta pantalla", "Información (borrar caché, desinstalar)")) { _, cual ->
                when (cual) {
                    0 -> ocultar(paquete)
                    1 -> runCatching {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paquete"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            .show()
    }

    private fun ocultas(): Set<String> =
        HashSet(prefs.getStringSet(OCULTAS, emptySet()) ?: emptySet())

    private fun ocultar(paquete: String) {
        prefs.edit().putStringSet(OCULTAS, ocultas() + paquete).apply()
        cargar()
    }

    private fun mostrarOcultas() {
        prefs.edit().remove(OCULTAS).apply()
        cargar()
    }

    private fun abrirAjustes() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------------------------------------------------------------- memoria

    private val am by lazy { getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }

    private fun libreMb(): Long {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / MB
    }

    private fun ponerMemoria() {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        memoria.text = "Memoria libre: ${info.availMem / MB} de ${info.totalMem / MB} MB"
    }

    /**
     * Cierra lo que quedo abierto en segundo plano.
     *
     * Estando en la pantalla de inicio, todas las demas apps estan en segundo
     * plano: es el mejor momento. killBackgroundProcesses solo toca procesos
     * que el sistema ya considera prescindibles; no cierra nada que este en
     * pantalla ni rompe servicios del sistema. Se recorren las apps de la
     * grilla y tambien las ocultas, que suelen ser las de fabrica.
     */
    private fun liberar() {
        val paquetes = apps.mapNotNull { it.packageName } + ocultas()
        val antes = libreMb()
        nombre.text = "Liberando memoria…"
        Thread {
            paquetes.forEach { runCatching { am.killBackgroundProcesses(it) } }
            // El sistema tarda un momento en devolver la memoria.
            Thread.sleep(1500)
            val despues = libreMb()
            runOnUiThread {
                val ganado = despues - antes
                nombre.text = if (ganado > 0) "Se liberaron $ganado MB" else "No habia nada para cerrar"
                ponerMemoria()
            }
        }.start()
    }

    // ------------------------------------------------------------------ hora

    private fun ponerHora() {
        val ahora = Date()
        reloj.text = HORA.format(ahora)
        fecha.text = DIA.format(ahora).replaceFirstChar { it.titlecase(ES) }
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

    private companion object {
        /** 16:9, igual que TV Lite. */
        const val ANCHO_DP = 160
        const val ALTO_DP = 90
        const val SEPARACION_DP = 14
        const val MARGEN_DP = 48

        const val BLANCO = 0xFFFFFFFF.toInt()
        const val TENUE = 0xB3FFFFFF.toInt()
        const val MB = 1024L * 1024L

        const val OCULTAS = "ocultas"
        const val TAG = "TVLiteRCA"

        // java.time no existe antes de Android 8: SimpleDateFormat si.
        @Suppress("DEPRECATION")
        val ES = Locale("es", "AR")
        val HORA = SimpleDateFormat("HH:mm", ES)
        val DIA = SimpleDateFormat("EEEE d 'de' MMMM", ES)
    }
}
