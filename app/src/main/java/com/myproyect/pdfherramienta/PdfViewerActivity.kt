package com.myproyect.pdfherramienta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        private const val RESOLUCION = 1600
    }

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    private lateinit var visor: EditorCanvasView
    private lateinit var txtTituloPdf: TextView
    private lateinit var txtEstado: TextView

    private var totalPaginas = 0
    private var cargando = false
    private var destruido = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pdf_viewer)

        visor = findViewById(R.id.visorPdf)
        txtTituloPdf = findViewById(R.id.txtTituloPdf)
        txtEstado = findViewById(R.id.txtEstado)

        visor.setModo(EditorCanvasView.Modo.MOVER)

        visor.alCambiarPaginaActual = { pagina ->
            if (totalPaginas > 0) {
                txtEstado.text =
                    "Página ${pagina + 1} de $totalPaginas"
            }
        }

        val btnEditar =
            findViewById<Button>(
                R.id.btnEditarDesdeVisor
            )

        btnEditar.setOnClickListener {

            val uri = intent.data

            if (uri != null) {

                val editarIntent =
                    Intent(
                        this,
                        PdfEditorActivity::class.java
                    )

                editarIntent.data = uri

                editarIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                startActivity(editarIntent)
            }
        }

        val uri = intent.data

        if (uri == null) {

            mensaje(
                "No se recibió ningún PDF."
            )

            finish()
            return
        }

        try {

            val archivo =
                copiarPdfTemporal(uri)

            descriptor =
                ParcelFileDescriptor.open(
                    archivo,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )

            renderer =
                PdfRenderer(descriptor!!)

            totalPaginas = renderer!!.pageCount

            txtTituloPdf.text =
                "PDF — $totalPaginas páginas"

            cargarPaginas()

        } catch (e: Exception) {

            mensaje(
                "No se pudo abrir el PDF:\n${e.message}"
            )

            finish()
        }
    }

    private fun cargarPaginas() {
        if (cargando) {
            return
        }

        val pdf = renderer ?: return
        cargando = true

        Thread {

            try {

                val bitmaps =
                    mutableListOf<Bitmap>()

                for (i in 0 until pdf.pageCount) {

                    if (destruido) {
                        return@Thread
                    }

                    bitmaps.add(
                        renderPagina(pdf, i)
                    )
                }

                runOnUiThread {

                    if (destruido) {
                        return@runOnUiThread
                    }

                    visor.setPaginas(
                        bitmaps,
                        emptyMap()
                    )

                    visor.irAPagina(0)

                    txtEstado.text =
                        "Página 1 de $totalPaginas"

                    cargando = false
                }

            } catch (e: Exception) {

                runOnUiThread {

                    if (destruido) {
                        return@runOnUiThread
                    }

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
        pdf: PdfRenderer,
        indice: Int
    ): Bitmap {
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

    private fun copiarPdfTemporal(
        uri: Uri
    ): File {

        val archivo =
            File(
                cacheDir,
                "documento_ver.pdf"
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

        destruido = true

        visor.reciclarPaginas()
        renderer?.close()
        descriptor?.close()

        super.onDestroy()
    }
}