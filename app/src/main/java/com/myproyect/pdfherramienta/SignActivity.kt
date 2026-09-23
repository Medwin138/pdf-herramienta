package com.myproyect.pdfherramienta

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class SignActivity : AppCompatActivity() {

    companion object {
        private const val RESOLUCION = 1600
    }

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    private lateinit var visor: EditorCanvasView
    private lateinit var txtPagina: TextView

    private var totalPaginas = 0
    private var paginaActual = 0
    private var guardando = false
    private var cargando = false

    private var ultimaFirma: Pair<Int, Int>? = null

    private val anotacionesPorPagina =
        mutableMapOf<Int, MutableList<Anotacion>>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_sign)

        visor = findViewById(R.id.visorFirmas)
        txtPagina = findViewById(R.id.txtPagina)

        val btnVolver =
            findViewById<Button>(R.id.btnVolver)
        val btnGuardar =
            findViewById<Button>(R.id.btnGuardar)
        val btnFirmar =
            findViewById<Button>(R.id.btnFirmar)
        val btnBorrarFirma =
            findViewById<Button>(R.id.btnBorrarFirma)

        visor.setModo(EditorCanvasView.Modo.MOVER)

        visor.alCambiarPaginaActual = {
            pagina ->
            paginaActual = pagina
            actualizarContador()
        }

        btnVolver.setOnClickListener {
            finish()
        }

        btnGuardar.setOnClickListener {
            guardarPdf()
        }

        btnFirmar.setOnClickListener {
            mostrarPanelFirma()
        }

        btnBorrarFirma.setOnClickListener {
            borrarFirma()
        }

        val uri = intent.data

        if (uri == null) {
            mensaje("No se recibió ningún PDF.")
            finish()
            return
        }

        try {
            val archivo = copiarPdfTemporal(uri)

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
     * Renderiza todas las páginas y las entrega
     * de una sola vez para navegar con desplazamiento
     * libre, igual que el visor.
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
                    bitmaps.add(
                        renderPagina(i)
                    )
                }

                runOnUiThread {

                    visor.setPaginas(
                        bitmaps,
                        anotacionesPorPagina
                    )

                    visor.irAPagina(0)
                    paginaActual = 0
                    actualizarContador()

                    mensaje(
                        "Desliza para navegar entre " +
                            "las páginas. 'Firmar' para " +
                            "colocar tu firma"
                    )

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
                visor.obtenerAnotaciones(i)
                    .toMutableList()
        }
    }

    private fun mostrarPanelFirma() {
        val densidad =
            resources.displayMetrics.density

        val pad =
            SignaturePadView(this)

        val altura =
            (160 * densidad).toInt()

        val contenedor =
            LinearLayout(this)

        contenedor.orientation =
            LinearLayout.VERTICAL

        contenedor.setPadding(
            (16 * densidad).toInt(),
            (8 * densidad).toInt(),
            (16 * densidad).toInt(),
            (8 * densidad).toInt()
        )

        contenedor.addView(
            pad,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                altura,
                1f
            )
        )

        val filaBotones =
            LinearLayout(this)

        filaBotones.orientation =
            LinearLayout.HORIZONTAL

        filaBotones.gravity = 1

        val btnBorrar =
            Button(this)

        btnBorrar.text = "Borrar"

        val btnMisFirmas =
            Button(this)

        btnMisFirmas.text = "Mis firmas"

        val btnUsar =
            Button(this)

        btnUsar.text = "Usar firma"

        val btnCancelar =
            Button(this)

        btnCancelar.text = "Cancelar"

        filaBotones.addView(btnBorrar)
        filaBotones.addView(btnMisFirmas)
        filaBotones.addView(btnUsar)
        filaBotones.addView(btnCancelar)

        val margen =
            (16 * densidad).toInt()

        val parametrosFila =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        parametrosFila.topMargin = margen

        contenedor.addView(
            filaBotones,
            parametrosFila
        )

        val dialogo =
            AlertDialog.Builder(this)
                .setTitle("Firma aquí")
                .setView(contenedor)
                .create()

        btnBorrar.setOnClickListener {
            pad.limpiar()
        }

        btnCancelar.setOnClickListener {
            dialogo.dismiss()
        }

        btnMisFirmas.setOnClickListener {
            dialogo.dismiss()
            mostrarMisFirmas()
        }

        btnUsar.setOnClickListener {
            if (!pad.tieneFirma()) {
                mensaje(
                    "Dibuja tu firma primero"
                )
                return@setOnClickListener
            }

            val firma =
                pad.generarFirma()

            FirmaGuardada.guardar(
                this,
                firma
            )

            dialogo.dismiss()

            ultimaFirma =
                visor.colocarFirma(firma)

            sincronizarAnotaciones()

            mensaje(
                "Firma colocada. Arrastra para moverla " +
                    "y pellizca para cambiar su tamaño"
            )
        }

        dialogo.show()
    }

    private fun mostrarMisFirmas() {
        val guardadas =
            FirmaGuardada.listar(this)

        if (guardadas.isEmpty()) {
            mensaje(
                "Aún no hay firmas guardadas. " +
                    "Dibuja una con 'Firmar' y " +
                    "se guardará automáticamente"
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
                    ultimaFirma =
                        visor.colocarFirma(imagen)
                    sincronizarAnotaciones()
                    mensaje(
                        "Firma colocada. Arrastra " +
                            "para moverla y pellizca " +
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

    private fun borrarFirma() {
        if (visor.haySeleccion()) {
            visor.eliminarSeleccionado()
            ultimaFirma = null
            mensaje("Firma borrada")
            return
        }

        val ultima = ultimaFirma

        if (ultima != null) {
            visor.eliminarAnotacion(
                ultima.first,
                ultima.second
            )
            ultimaFirma = null
            mensaje("Firma borrada")
            return
        }

        mensaje("No hay firmas que borrar")
    }

    private fun guardarPdf() {
        if (guardando) {
            return
        }

        DialogoGuardarComo.mostrar(
            this,
            "documento"
        ) { nombre ->
            guardarPdfConNombre(nombre)
        }
    }

    private fun guardarPdfConNombre(
        nombre: String
    ) {
        if (guardando) {
            return
        }

        guardando = true

        sincronizarAnotaciones()

        Thread {
            try {
                val uri = crearPdfFirmado(nombre)

                runOnUiThread {
                    mostrarGuardado(uri, nombre)
                }

            } catch (e: Throwable) {
                runOnUiThread {
                    mensaje("Error al guardar:\n${e.message}")
                }

            } finally {
                guardando = false
            }
        }.start()
    }

    private fun crearPdfFirmado(
        nombre: String
    ): Uri {
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
                    if (
                        anotacion
                        is ImagenAnotacion
                    ) {
                        guardarImagenRotada(
                            canvas,
                            anotacion
                        )
                    }
                }

                documento.finishPage(paginaPdf)

                bitmap.recycle()
            }

            return guardarDocumento(
                documento,
                nombre
            )

        } finally {
            documento.close()
        }
    }

    private fun guardarImagenRotada(
        canvas: Canvas,
        anotacion: ImagenAnotacion
    ) {
        val mitadAncho =
            anotacion.ancho / 2f
        val mitadAlto =
            anotacion.alto / 2f

        canvas.save()
        canvas.translate(
            anotacion.x + mitadAncho,
            anotacion.y + mitadAlto
        )
        canvas.rotate(anotacion.rotacion)
        canvas.drawBitmap(
            anotacion.imagen,
            null,
            RectF(
                -mitadAncho,
                -mitadAlto,
                mitadAncho,
                mitadAlto
            ),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        canvas.restore()
    }

    private fun guardarDocumento(
        documento: PdfDocument,
        nombre: String
    ): Uri {
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

    private fun mostrarGuardado(
        uri: Uri,
        nombre: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("PDF firmado")
            .setMessage(
                "El PDF se guardó en Descargas como:\n" +
                    nombre
            )
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
                "documento_firmar.pdf"
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
        visor.reciclarPaginas()
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}