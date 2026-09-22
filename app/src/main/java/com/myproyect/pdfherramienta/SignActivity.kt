package com.myproyect.pdfherramienta

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
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

    private lateinit var visor: SignPlacementView
    private lateinit var txtPagina: TextView
    private lateinit var btnPaginaAnterior: Button
    private lateinit var btnPaginaSiguiente: Button

    private var paginaActual = 0
    private var indiceActualCargado: Int? = null
    private var guardando = false

    private val sellosPorPagina =
        mutableMapOf<Int, MutableList<SelloFirma>>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_sign)

        visor = findViewById(R.id.visorFirmas)
        txtPagina = findViewById(R.id.txtPagina)
        btnPaginaAnterior =
            findViewById(R.id.btnPaginaAnterior)
        btnPaginaSiguiente =
            findViewById(R.id.btnPaginaSiguiente)

        val btnVolver =
            findViewById<Button>(R.id.btnVolver)
        val btnGuardar =
            findViewById<Button>(R.id.btnGuardar)
        val btnFirmar =
            findViewById<Button>(R.id.btnFirmar)
        val btnBorrarFirma =
            findViewById<Button>(R.id.btnBorrarFirma)

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
            val borrado =
                visor.borrarSeleccionado()

            if (!borrado) {
                mensaje(
                    "No hay firmas que borrar"
                )
            }
        }

        btnPaginaAnterior.setOnClickListener {
            cargarPagina(paginaActual - 1)
        }

        btnPaginaSiguiente.setOnClickListener {
            cargarPagina(paginaActual + 1)
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
            sellosPorPagina[it] =
                visor.obtenerSellos()
                    .toMutableList()
        }

        visor.setPagina(
            renderPagina(indice),
            sellosPorPagina[indice]
                ?: emptyList()
        )

        indiceActualCargado = indice
        paginaActual = indice
        actualizarBarra()
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

        val btnUsar =
            Button(this)

        btnUsar.text = "Usar firma"

        val btnCancelar =
            Button(this)

        btnCancelar.text = "Cancelar"

        filaBotones.addView(btnBorrar)
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

        btnUsar.setOnClickListener {
            if (!pad.tieneFirma()) {
                mensaje(
                    "Dibuja tu firma primero"
                )
                return@setOnClickListener
            }

            val firma =
                pad.generarFirma()

            dialogo.dismiss()

            visor.addSello(firma)
        }

        dialogo.show()
    }

    private fun guardarPdf() {
        if (guardando) {
            return
        }

        guardando = true

        indiceActualCargado?.let {
            sellosPorPagina[it] =
                visor.obtenerSellos()
                    .toMutableList()
        }

        Thread {
            try {
                val uri = crearPdfFirmado()

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

    private fun crearPdfFirmado(): Uri {
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
                    sello
                    in sellosPorPagina[i]
                        ?: mutableListOf()
                ) {
                    canvas.drawBitmap(
                        sello.imagen,
                        null,
                        RectF(
                            sello.x,
                            sello.y,
                            sello.x + sello.ancho,
                            sello.y + sello.alto
                        ),
                        Paint(
                            Paint.FILTER_BITMAP_FLAG
                        )
                    )
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
            "pdf_firmado_" +
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
            .setTitle("PDF firmado")
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
        visor.reciclar()
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}