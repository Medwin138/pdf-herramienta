package com.myproyect.pdfherramienta

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.FileOutputStream

class PdfEditorActivity : AppCompatActivity() {

    companion object {
        private const val RESOLUCION = 1600
    }

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    private lateinit var editor: EditorCanvasView
    private lateinit var txtPagina: TextView

    private var totalPaginas = 0
    private var paginaActual = 0
    private var guardando = false
    private var cargando = false

    private val anotacionesPorPagina =
        mutableMapOf<Int, MutableList<Anotacion>>()

    private val metricasPorPagina =
        mutableMapOf<Int, Pair<Int, Int>>()

    private val textosPorPagina =
        mutableMapOf<Int, List<TextoReconocido>>()

    private var archivoPdf: File? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pdf_editor)

        editor = findViewById(R.id.editorCanvas)
        txtPagina = findViewById(R.id.txtPagina)

        val btnVolver =
            findViewById<Button>(R.id.btnVolver)
        val btnGuardar =
            findViewById<Button>(R.id.btnGuardar)
        val btnDibujo =
            findViewById<Button>(R.id.btnDibujo)
        val btnTexto =
            findViewById<Button>(R.id.btnTexto)
        val btnMover =
            findViewById<Button>(R.id.btnMover)
        val btnGirar =
            findViewById<Button>(R.id.btnGirar)
        val btnBorrar =
            findViewById<Button>(R.id.btnBorrar)
        val btnDetectar =
            findViewById<Button>(R.id.btnDetectar)
        val btnDeshacer =
            findViewById<Button>(R.id.btnDeshacer)
        val btnColorRojo =
            findViewById<Button>(R.id.btnColorRojo)
        val btnColorAzul =
            findViewById<Button>(R.id.btnColorAzul)
        val btnColorNegro =
            findViewById<Button>(R.id.btnColorNegro)
        val btnResaltar =
            findViewById<Button>(R.id.btnResaltar)
        val btnSubrayar =
            findViewById<Button>(R.id.btnSubrayar)
        val btnFormas =
            findViewById<Button>(R.id.btnFormas)
        val btnFirmas =
            findViewById<Button>(R.id.btnFirmas)

        btnVolver.setOnClickListener {
            finish()
        }

        btnGuardar.setOnClickListener {
            guardarPdf()
        }

        btnDibujo.setOnClickListener {
            editor.setModo(
                EditorCanvasView.Modo.DIBUJO
            )
            mensaje("Modo dibujo: arrastra el dedo para anotar")
        }

        btnTexto.setOnClickListener {
            pedirTexto()
        }

        btnMover.setOnClickListener {
            editor.setModo(
                EditorCanvasView.Modo.MOVER
            )
            mensaje("Modo mover: arrastra el texto o el documento")
        }

        btnGirar.setOnClickListener {

            val texto =
                editor.textoSeleccionado()

            if (texto == null) {
                mensaje(
                    "Coloca primero un texto y tócalo " +
                        "para seleccionarlo"
                )
            } else {
                editor.girarSeleccionado(15f)
            }
        }

        btnBorrar.setOnClickListener {
            editor.setModo(
                EditorCanvasView.Modo.BORRAR
            )
            mensaje(
                "Modo borrar: arrastra sobre el " +
                    "texto para ocultarlo"
            )
        }

        btnDetectar.setOnClickListener {
            editor.setModo(
                EditorCanvasView.Modo.DETECTAR
            )
            mensaje(
                "Toca sobre un texto del PDF " +
                    "para detectar su fuente y tamaño"
            )
        }

        editor.alDetectarTexto = {
                pagina,
                detectado ->
            if (detectado == null) {
                mensaje(
                    "No hay texto reconocible aquí. " +
                        "Toca sobre las letras del PDF"
                )
                editor.setModo(
                    EditorCanvasView.Modo.MOVER
                )
            } else {
                val texto = detectado.texto

                val fuente =
                    TextoReconocimiento.fuenteElegida(
                        detectado.nombreFuente
                    )

                val anchoPts =
                    metricasPorPagina[pagina]
                        ?.first
                        ?: 595

                val tamano =
                    TextoReconocimiento.tamanoEnEditor(
                        detectado.tamanoFuente,
                        anchoPts,
                        RESOLUCION
                    )

                mensaje(
                    "Detectado: '$texto' " +
                        "· fuente $fuente " +
                        "· tamaño $tamano"
                )

                editor.setModo(
                    EditorCanvasView.Modo.MOVER
                )

                pedirTexto(
                    textoDetectado = texto,
                    fuenteDetectada = fuente,
                    tamanoDetectado = tamano
                )
            }
        }

        btnDeshacer.setOnClickListener {
            editor.deshacer()
            sincronizarAnotaciones()
        }

        btnColorRojo.setOnClickListener {
            editor.setColor(Color.RED)
        }

        btnColorAzul.setOnClickListener {
            editor.setColor(Color.BLUE)
        }

        btnColorNegro.setOnClickListener {
            editor.setColor(Color.BLACK)
        }

        btnResaltar.setOnClickListener {
            editor.setModo(
                EditorCanvasView.Modo.RESALTAR
            )
            mensaje(
                "Resaltar: arrastra sobre el " +
                    "texto para marcarlo en amarillo"
            )
        }

        btnSubrayar.setOnClickListener {
            editor.setModo(
                EditorCanvasView.Modo.SUBRAYAR
            )
            mensaje(
                "Subrayar: traza una línea bajo el texto"
            )
        }

        btnFormas.setOnClickListener {

            val nombres =
                arrayOf(
                    "Línea",
                    "Flecha",
                    "Rectángulo",
                    "Elipse",
                    "Caja de texto"
                )

            val tipos =
                arrayOf(
                    EditorCanvasView.TipoForma.LINEA,
                    EditorCanvasView.TipoForma.FLECHA,
                    EditorCanvasView.TipoForma.RECTANGULO,
                    EditorCanvasView.TipoForma.ELIPSE,
                    EditorCanvasView.TipoForma.CAJA_TEXTO
                )

            AlertDialog.Builder(this)
                .setTitle("Forma")
                .setItems(nombres) { _, cual ->
                    editor.tipoFormaSeleccionada =
                        tipos[cual]
                    editor.setModo(
                        EditorCanvasView.Modo.FORMAS
                    )
                    mensaje(
                        "Arrastra el dedo para dibujar " +
                            "la ${nombres[cual].toLowerCase()}"
                    )
                }
                .show()
        }

editor.alColocarForma = {
            sincronizarAnotaciones()
            mensaje(
                "Forma colocada. Tócala para moverla"
            )
        }

        btnFirmas.setOnClickListener {
            mostrarLibreriaFirmas()
        }

        editor.alColocarTexto = {
            sincronizarAnotaciones()
            mensaje(
                "Texto colocado. En modo mover, " +
                    "arrastra para reposicionarlo"
            )
        }

        editor.alCambiarPaginaActual = {
            pagina ->
            paginaActual = pagina
            actualizarContador()
        }

        val uri = intent.data

        if (uri == null) {
            mensaje("No se recibió ningún PDF.")
            finish()
            return
        }

        try {
            val archivo = copiarPdfTemporal(uri)
            archivoPdf = archivo

            descriptor = ParcelFileDescriptor.open(
                archivo,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            renderer = PdfRenderer(descriptor!!)
            totalPaginas = renderer!!.pageCount
            actualizarContador()

            cargarTodasLasPaginas()

        } catch (e: Exception) {
            mensaje("No se pudo abrir el PDF:\n${e.message}")
            finish()
        }
    }

    /*
     * Renderiza todas las páginas en segundo plano
     * y se las entrega al editor de una sola vez,
     * tal como el visor trabaja con todo el documento.
     */
    private fun cargarTodasLasPaginas() {
        if (cargando) {
            return
        }

        val pdf = renderer ?: return
        cargando = true

        mensaje("Cargando documento…")

        Thread {

            try {

                val bitmaps =
                    mutableListOf<Bitmap>()

                for (i in 0 until pdf.pageCount) {

                    val pagina =
                        pdf.openPage(i)

                    val anchoPts =
                        pagina.width
                    val altoPts =
                        pagina.height

                    metricasPorPagina[i] =
                        Pair(anchoPts, altoPts)

                    val escala =
                        RESOLUCION.toFloat() /
                            anchoPts.toFloat()

                    val alto =
                        (altoPts * escala).toInt()

                    val bitmap =
                        Bitmap.createBitmap(
                            RESOLUCION,
                            alto,
                            Bitmap.Config.ARGB_8888
                        )

                    bitmap.eraseColor(Color.WHITE)

                    pagina.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    pagina.close()

                    bitmaps.add(bitmap)
                }

                runOnUiThread {

                    editor.setPaginas(
                        bitmaps,
                        anotacionesPorPagina
                    )

                    editor.irAPagina(0)

                    mensaje(
                        "Documento cargado: $totalPaginas " +
                            "página(s). Desliza para navegar"
                    )

                    reconocerTodasLasPaginas()

                    cargando = false
                }

            } catch (e: Exception) {

                runOnUiThread {

                    mensaje(
                        "No se pudo renderizar el PDF:\n" +
                            e.message
                    )
                    cargando = false
                }
            }

        }.start()
    }

    private fun reconocerTodasLasPaginas() {
        val archivo = archivoPdf ?: return

        Thread {

            try {
                PDFBoxResourceLoader.init(
                    applicationContext
                )
            } catch (e: Exception) {
            }

            for (i in 0 until totalPaginas) {

                if (textosPorPagina.containsKey(i)) {
                    continue
                }

                val metricas =
                    metricasPorPagina[i]
                        ?: continue

                val textos =
                    try {
                        TextoReconocimiento.reconocerPagina(
                            archivo,
                            i,
                            metricas.first,
                            metricas.second,
                            RESOLUCION
                        )
                    } catch (e: Exception) {
                        emptyList<TextoReconocido>()
                    }

                runOnUiThread {
                    textosPorPagina[i] = textos
                    editor.setTextosReconocidos(
                        i,
                        textos
                    )
                }
            }

        }.start()
    }

    private fun renderPagina(
        indice: Int
    ): Bitmap {
        val pdf = renderer
            ?: throw Exception("PDF no disponible")

        val pagina = pdf.openPage(indice)

        val escala =
            RESOLUCION.toFloat() /
                pagina.width.toFloat()

        val alto =
            (pagina.height * escala).toInt()

        val bitmap =
            Bitmap.createBitmap(
                RESOLUCION,
                alto,
                Bitmap.Config.ARGB_8888
            )

        bitmap.eraseColor(Color.WHITE)

        pagina.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

        pagina.close()

        return bitmap
    }

    private fun actualizarContador() {
        if (totalPaginas <= 0) {
            return
        }

        txtPagina.text =
            "${paginaActual + 1} / $totalPaginas"
    }

    private fun sincronizarAnotaciones() {
        for (i in 0 until totalPaginas) {
            anotacionesPorPagina[i] =
                editor.obtenerAnotaciones(i)
                    .toMutableList()
        }
    }

    private fun pedirTexto(
        textoDetectado: String? = null,
        fuenteDetectada: String? = null,
        tamanoDetectado: Int? = null
    ) {

        val raiz =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(40, 20, 40, 10)
            }

        val entrada =
            EditText(this).apply {
                hint = "Escribe el texto a añadir"
                if (!textoDetectado.isNullOrBlank()) {
                    setText(textoDetectado)
                }
            }

        val contenedorPreview =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(
                    Color.parseColor("#EEEEEE")
                )
                setPadding(0, 24, 0, 24)
            }

        val preview =
            TextView(this).apply {
                text = "A B C"
                textSize = 24f
                setTextColor(Color.BLACK)
                setPadding(16, 8, 16, 8)
            }

        contenedorPreview.addView(
            preview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val lblFuente =
            TextView(this).apply {
                text = "Fuente: Sans Serif"
                setPadding(0, 16, 0, 4)
            }

        val nombresFuente =
            arrayOf(
                "Sans Serif",
                "Dancing Script",
                "Oswald",
                "Bebas Neue",
                "Playfair Display",
                "Raleway",
                "Serif",
                "Monospace"
            )

        val clavesFuente =
            arrayOf(
                "sans",
                "dancing",
                "oswald",
                "bebas",
                "playfair",
                "raleway",
                "serif",
                "mono"
            )

        val typefaces =
            clavesFuente.map { clave ->
                cargarTypeface(clave, false)
            }

        var fuenteActual =
            fuenteDetectada
                ?.takeIf {
                    it in clavesFuente
                }
                ?: "sans"

        lateinit var actualizarPreview: () -> Unit

        val indiceFuenteInicial =
            clavesFuente.indexOf(fuenteActual)
                .coerceAtLeast(0)

        lblFuente.text =
            "Fuente: ${nombresFuente[indiceFuenteInicial]}"

        val btnFuente =
            Button(this).apply {
                text = "Cambiar fuente"
            }

        btnFuente.setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Fuente")
                .setItems(nombresFuente) {
                        _,
                        cual ->

                    fuenteActual =
                        clavesFuente[cual]
                    lblFuente.text =
                        "Fuente: ${nombresFuente[cual]}"
                    actualizarPreview()
                }
                .show()
        }

        val lblTamano =
            TextView(this).apply {
                text = "Tamaño: 48"
                setPadding(0, 16, 0, 4)
            }

        val barraTamano =
            SeekBar(this).apply {
                max = 200
                progress =
                    tamanoDetectado?.coerceIn(12, 200)
                        ?: 48
            }

        barraTamano.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progreso: Int,
                    desdeUsuario: Boolean
                ) {
                    val tamano =
                        if (progreso < 12) {
                            12
                        } else {
                            progreso
                        }
                    lblTamano.text =
                        "Tamaño: $tamano"
                    actualizarPreview()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar
                ) {
                }
            }
        )

        val checkNegrita =
            CheckBox(this).apply {
                text = "Negrita"
                setOnClickListener {
                    actualizarPreview()
                }
            }

        checkNegrita.setTextColor(
            Color.BLACK
        )

        val lblRotacion =
            TextView(this).apply {
                text = "Rotación: 0°"
                setPadding(0, 16, 0, 4)
            }

        val barraRotacion =
            SeekBar(this).apply {
                max = 360
                progress = 180
            }

        barraRotacion.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progreso: Int,
                    desdeUsuario: Boolean
                ) {
                    val grados =
                        progreso - 180
                    lblRotacion.text =
                        "Rotación: $grados°"
                    actualizarPreview()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar
                ) {
                }
            }
        )

        val lblColor =
            TextView(this).apply {
                text = "Color:"
                setPadding(0, 16, 0, 4)
            }

        val filaColores =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        val colorSeleccion = intArrayOf(Color.BLACK)
        val muestras =
            listOf(
                Pair(
                    "Rojo",
                    Color.parseColor("#D32F2F")
                ),
                Pair(
                    "Azul",
                    Color.parseColor("#1565C0")
                ),
                Pair(
                    "Negro",
                    Color.parseColor("#212121")
                ),
                Pair(
                    "Verde",
                    Color.parseColor("#2E7D32")
                ),
                Pair(
                    "Morado",
                    Color.parseColor("#6A1B9A")
                ),
                Pair(
                    "Naranja",
                    Color.parseColor("#EF6C00")
                )
            )

        muestras.forEach { (nombre, hex) ->

            val densidad =
                resources.displayMetrics.density

            filaColores.addView(
                Button(this).apply {

                    text = nombre
                    setTextColor(hex)
                    textSize = 12f

                    setOnClickListener {
                        colorSeleccion[0] = hex
                        actualizarPreview()
                    }
                },
                LinearLayout.LayoutParams(
                    0,
                    (40 * densidad).toInt(),
                    1f
                ).apply {
                    marginEnd = 6
                }
            )
        }

        actualizarPreview = {
            val texto = entrada.text.toString()

            preview.text =
                if (texto.isBlank()) {
                    "Muestra"
                } else {
                    texto
                }

            val tamano =
                if (barraTamano.progress < 12) {
                    12
                } else {
                    barraTamano.progress
                }

            val rotacion =
                barraRotacion.progress - 180

            val indice =
                clavesFuente.indexOf(fuenteActual)
                    .coerceAtLeast(0)

            preview.typeface =
                if (checkNegrita.isChecked) {
                    cargarTypeface(
                        fuenteActual,
                        true
                    )
                } else {
                    typefaces[indice]
                }

            preview.textSize = tamano.toFloat()
            preview.setTextColor(colorSeleccion[0])
            preview.rotation = rotacion.toFloat()
        }

        entrada.addTextChangedListener(
            object :
                android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {
                    actualizarPreview()
                }
            }
        )

        actualizarPreview()

        raiz.addView(
            entrada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        raiz.addView(contenedorPreview)
        raiz.addView(lblFuente)
        raiz.addView(btnFuente)
        raiz.addView(lblTamano)
        raiz.addView(barraTamano)
        raiz.addView(checkNegrita)
        raiz.addView(lblRotacion)
        raiz.addView(barraRotacion)
        raiz.addView(lblColor)
        raiz.addView(filaColores)

        AlertDialog.Builder(this)
            .setTitle("Añadir texto")
            .setView(raiz)
            .setPositiveButton("Aceptar") { _, _ ->
                val texto =
                    entrada.text.toString()

                if (texto.isBlank()) {
                    return@setPositiveButton
                }

                val tamano =
                    if (barraTamano.progress < 12) {
                        12
                    } else {
                        barraTamano.progress
                    }

                val grados =
                    barraRotacion.progress - 180

                val typeface =
                    cargarTypeface(
                        fuenteActual,
                        checkNegrita.isChecked
                    )

                val pintura =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = colorSeleccion[0]
                        textSize = tamano.toFloat()
                        this.typeface = typeface

                        if (checkNegrita.isChecked) {
                            isFakeBoldText = false
                        }
                    }

                editor.setModo(
                    EditorCanvasView.Modo.TEXTO
                )
                editor.setTexto(
                    texto,
                    pintura,
                    grados.toFloat()
                )
                mensaje(
                    "Toca sobre la página " +
                        "para colocar el texto"
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cargarTypeface(
        clave: String,
        negrita: Boolean
    ): Typeface {

        val estilo =
            if (negrita) {
                Typeface.BOLD
            } else {
                Typeface.NORMAL
            }

        return when (clave) {
            "dancing" ->
                Typeface.createFromAsset(
                    assets,
                    "fonts/dancing_script.ttf"
                )
            "oswald" ->
                Typeface.createFromAsset(
                    assets,
                    "fonts/oswald.ttf"
                )
            "bebas" ->
                Typeface.createFromAsset(
                    assets,
                    "fonts/bebas_neue.ttf"
                )
            "playfair" ->
                Typeface.createFromAsset(
                    assets,
                    "fonts/playfair_display.ttf"
                )
            "raleway" ->
                Typeface.createFromAsset(
                    assets,
                    "fonts/raleway.ttf"
                )
            "serif" ->
                Typeface.create(
                    Typeface.SERIF,
                    estilo
                )
            "mono" ->
                Typeface.create(
                    Typeface.MONOSPACE,
                    estilo
                )
            else ->
                Typeface.create(
                    Typeface.SANS_SERIF,
                    estilo
                )
        }
    }

    private fun mostrarLibreriaFirmas() {
        val guardadas =
            FirmaGuardada.listar(this)

        if (guardadas.isEmpty()) {
            mensaje(
                "Aún no hay firmas guardadas. " +
                    "Dibuja una en 'Firmar PDF' y " +
                    "se guardará automáticamente aquí"
            )
            return
        }

        val nombres =
            guardadas.mapIndexed {
                i, archivo ->
                val marca =
                    archivo.nameWithoutExtension
                        .removePrefix("firma_")
                        .take(14)
                val fecha = try {
                    java.text.SimpleDateFormat(
                        "dd/MM HH:mm"
                    ).format(archivo.lastModified())
                } catch (e: Exception) {
                    ""
                }
                "${i + 1}. $marca ($fecha)"
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Mis firmas")
            .setItems(nombres) { _, cual ->
                val imagen =
                    FirmaGuardada.cargar(
                        guardadas[cual]
                    )
                if (imagen != null) {
                    editor.colocarFirma(imagen)
                    sincronizarAnotaciones()
                    mensaje(
                        "Firma colocada. Tócala " +
                            "para moverla o pellizca " +
                            "para cambiar su tamaño"
                    )
                } else {
                    mensaje(
                        "No se pudo cargar la firma"
                    )
                }
            }
            .setNegativeButton("Vaciar librería") {
                _, _ ->
                confirmarVaciarFirmas()
            }
            .show()
    }

    private fun confirmarVaciarFirmas() {
        if (
            FirmaGuardada.listar(this)
                .isEmpty()
        ) {
            mensaje("No hay firmas guardadas")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Vaciar librería de firmas")
            .setMessage(
                "Se eliminarán todas las firmas " +
                    "guardadas. ¿Continuar?"
            )
            .setPositiveButton(
                "Sí, eliminar"
            ) { _, _ ->
                FirmaGuardada.vaciar(this)
                mensaje(
                    "Librería de firmas vaciada"
                )
            }
            .setNegativeButton(
                "Cancelar",
                null
            )
            .show()
    }

    private fun guardarPdf() {
        if (guardando) {
            return
        }

        guardando = true

        sincronizarAnotaciones()

        Thread {
            try {
                val uri = crearPdfEditado()

                runOnUiThread {
                    mostrarGuardado(uri)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    mensaje("Error al guardar:\n${e.message}")
                }

            } finally {
                guardando = false
            }
        }.start()
    }

    private fun crearPdfEditado(): Uri {
        val pdf = renderer
            ?: throw Exception("PDF no disponible")

        val documento = PdfDocument()

        try {

            for (i in 0 until pdf.pageCount) {

                val paginaOrigen =
                    pdf.openPage(i)

                val anchoPagina =
                    paginaOrigen.width
                val altoPagina =
                    paginaOrigen.height

                paginaOrigen.close()

                val bitmap = renderPagina(i)

                val info =
                    PdfDocument.PageInfo.Builder(
                        anchoPagina,
                        altoPagina,
                        i + 1
                    ).create()

                val paginaPdf =
                    documento.startPage(info)

                val canvas =
                    paginaPdf.canvas

                val escala =
                    anchoPagina.toFloat() /
                        bitmap.width.toFloat()

                canvas.scale(escala, escala)

                canvas.drawBitmap(
                    bitmap,
                    0f,
                    0f,
                    null
                )

                for (
                    anotacion
                    in anotacionesPorPagina[i]
                        ?: mutableListOf()
                ) {
                    anotacion.dibujar(canvas)
                }

                documento.finishPage(paginaPdf)

                bitmap.recycle()
            }

            return guardarDocumento(documento)

        } finally {
            documento.close()
        }
    }

    private fun guardarDocumento(
        documento: PdfDocument
    ): Uri {
        val nombre =
            "pdf_editado_" +
                System.currentTimeMillis() +
                ".pdf"

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            guardarEnDescargas(
                documento,
                nombre
            )
        } else {
            guardarEnArchivoApp(
                documento,
                nombre
            )
        }
    }

    private fun guardarEnDescargas(
        documento: PdfDocument,
        nombre: String
    ): Uri {
        val valores =
            ContentValues().apply {

                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    nombre
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/pdf"
                )

                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )

                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )
            }

        val uri =
            contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                valores
            ) ?: throw Exception(
                "No se pudo crear el archivo"
            )

        contentResolver
            .openOutputStream(uri)
            ?.use { salida ->
                documento.writeTo(salida)
            } ?: throw Exception(
                "No se pudo escribir el archivo"
            )

        val limpiar =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    0
                )
            }

        contentResolver.update(
            uri,
            limpiar,
            null,
            null
        )

        return uri
    }

    private fun guardarEnArchivoApp(
        documento: PdfDocument,
        nombre: String
    ): Uri {
        val directorio =
            getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: filesDir

        val archivo =
            File(
                directorio,
                nombre
            )

        FileOutputStream(archivo).use {
            documento.writeTo(it)
        }

        return FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            archivo
        )
    }

    private fun mostrarGuardado(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("PDF editado")
            .setMessage("El PDF se guardó en la carpeta Descargas.")
            .setPositiveButton("Compartir") { _, _ ->
                compartir(uri)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun compartir(uri: Uri) {
        val intent =
            Intent(Intent.ACTION_SEND)

        intent.type = "application/pdf"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        startActivity(
            Intent.createChooser(
                intent,
                "Compartir PDF"
            )
        )
    }

    private fun copiarPdfTemporal(
        uri: Uri
    ): File {
        val archivo =
            File(
                cacheDir,
                "documento_editar.pdf"
            )

        contentResolver
            .openInputStream(uri)
            ?.use { entrada ->

                FileOutputStream(archivo)
                    .use { salida ->
                        entrada.copyTo(salida)
                    }
            }
            ?: throw Exception(
                "No se pudo leer el archivo."
            )

        return archivo
    }

    private fun mensaje(texto: String) {
        Toast.makeText(
            this,
            texto,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        editor.reciclarPaginas()
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}