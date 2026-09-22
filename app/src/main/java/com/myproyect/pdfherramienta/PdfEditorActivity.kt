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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
    private lateinit var btnPaginaAnterior: Button
    private lateinit var btnPaginaSiguiente: Button

    private var paginaActual = 0
    private var indiceActualCargado: Int? = null
    private var guardando = false

    private val anotacionesPorPagina =
        mutableMapOf<Int, MutableList<Anotacion>>()

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
        btnPaginaAnterior =
            findViewById(R.id.btnPaginaAnterior)
        btnPaginaSiguiente =
            findViewById(R.id.btnPaginaSiguiente)

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
        val btnLimpiar =
            findViewById<Button>(R.id.btnLimpiar)
        val btnColorRojo =
            findViewById<Button>(R.id.btnColorRojo)
        val btnColorAzul =
            findViewById<Button>(R.id.btnColorAzul)
        val btnColorNegro =
            findViewById<Button>(R.id.btnColorNegro)

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
            mensaje("Modo mover: arrastra el texto o la página")
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

        editor.alDetectarTexto = { detectado ->
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
                    renderer
                        ?.openPage(paginaActual)
                        ?.width
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
        }

        btnLimpiar.setOnClickListener {
            editor.limpiar()
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

        btnPaginaAnterior.setOnClickListener {
            cargarPagina(paginaActual - 1)
        }

        btnPaginaSiguiente.setOnClickListener {
            cargarPagina(paginaActual + 1)
        }

        editor.alColocarTexto = {

            mensaje(
                "Texto colocado. En modo mover, " +
                    "arrastra para reposicionarlo"
            )
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

            cargarPagina(0)

        } catch (e: Exception) {
            mensaje("No se pudo abrir el PDF:\n${e.message}")
            finish()
        }
    }

    private fun cargarPagina(indice: Int) {
        val pdf = renderer ?: return

        if (
            indice < 0 ||
            indice >= pdf.pageCount
        ) {
            return
        }

        indiceActualCargado?.let {
            anotacionesPorPagina[it] =
                editor.obtenerAnotaciones()
                    .toMutableList()
        }

        val pagina =
            pdf.openPage(indice)

        val anchoPts =
            pagina.width
        val altoPts =
            pagina.height

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

        editor.setPagina(
            bitmap,
            anotacionesPorPagina[indice]
                ?: emptyList()
        )

        editor.setTextosReconocidos(
            textosPorPagina[indice]
                ?: emptyList()
        )

        indiceActualCargado = indice
        paginaActual = indice
        actualizarBarra()

        reconocerTextoPagina(
            indice,
            anchoPts,
            altoPts
        )
    }

    private fun reconocerTextoPagina(
        indice: Int,
        anchoPts: Int,
        altoPts: Int
    ) {
        val archivo = archivoPdf ?: return

        if (textosPorPagina.containsKey(indice)) {
            return
        }

        Thread {

            try {

                val textos =
                    TextoReconocimiento.reconocerPagina(
                        archivo,
                        indice,
                        anchoPts,
                        altoPts,
                        RESOLUCION
                    )

                runOnUiThread {
                    if (indice == paginaActual) {
                        editor.setTextosReconocidos(
                            textos
                        )
                    }
                    textosPorPagina[indice] = textos
                }

            } catch (e: Exception) {
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

    private fun actualizarBarra() {
        val total = renderer?.pageCount ?: 1

        txtPagina.text =
            "${paginaActual + 1} / $total"

        btnPaginaAnterior.isEnabled =
            paginaActual > 0
        btnPaginaSiguiente.isEnabled =
            paginaActual < total - 1
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

        val colorSeleccion = intArrayOf(Color.RED)
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

            filaColores.addView(
                Button(this).apply {

                    text = nombre
                    setTextColor(hex)
                    textSize = 13f

                    setOnClickListener {
                        colorSeleccion[0] = hex
                        actualizarPreview()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
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

    private fun guardarPdf() {
        if (guardando) {
            return
        }

        guardando = true

        indiceActualCargado?.let {
            anotacionesPorPagina[it] =
                editor.obtenerAnotaciones()
                    .toMutableList()
        }

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
                    Environment.DIRECTORY_DOCUMENTS
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
        editor.reciclarPagina()
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}