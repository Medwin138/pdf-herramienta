package com.myproyect.pdfherramienta

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class ImagesToPdfActivity : AppCompatActivity() {

    private enum class TamanoPagina {
        SEGUN_IMAGEN,
        A4_VERTICAL,
        A4_HORIZONTAL
    }

    companion object {
        const val EXTRA_IMAGENES = "imagenes"
        private const val MAX_DIMENCION = 2048

        private const val ANCHO_A4 = 595
        private const val ALTO_A4 = 842
        private const val MARGEN = 24f
    }

    private val imagenes = mutableListOf<Uri>()

    private lateinit var contenedorMiniaturas: LinearLayout
    private lateinit var txtInfo: TextView
    private lateinit var radioTamanos: RadioGroup

    private var guardando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_images_to_pdf)

        contenedorMiniaturas =
            findViewById(R.id.contenedorMiniaturas)
        txtInfo = findViewById(R.id.txtInfo)
        radioTamanos =
            findViewById(R.id.radioTamanos)

        val btnVolver =
            findViewById<Button>(R.id.btnVolver)
        val btnCrearPdf =
            findViewById<Button>(R.id.btnCrearPdf)

        btnVolver.setOnClickListener {
            finish()
        }

        btnCrearPdf.setOnClickListener {
            crearPdf()
        }

        val uris =
            intent.getStringArrayListExtra(
                EXTRA_IMAGENES
            )

        if (uris.isNullOrEmpty()) {
            mensaje("No se recibieron imágenes.")
            finish()
            return
        }

        for (texto in uris) {
            imagenes.add(Uri.parse(texto))
        }

        cargarMiniaturas()
        actualizarInfo()
    }

    private fun tamanoSeleccionado(): TamanoPagina {
        val idSeleccionado =
            radioTamanos.checkedRadioButtonId

        return when (idSeleccionado) {
            R.id.rbA4Vertical ->
                TamanoPagina.A4_VERTICAL
            R.id.rbA4Horizontal ->
                TamanoPagina.A4_HORIZONTAL
            else ->
                TamanoPagina.SEGUN_IMAGEN
        }
    }

    private fun cargarMiniaturas() {
        contenedorMiniaturas.removeAllViews()

        if (imagenes.isEmpty()) {
            actualizarInfo()
            return
        }

        val densidad =
            resources.displayMetrics.density

        val tamanoMiniatura =
            (110 * densidad).toInt()

        Thread {
            val vistas =
                mutableListOf<ImageView>()

            for (
                index in imagenes.indices
            ) {
                val miniatura =
                    try {
                        decodificarImagen(
                            imagenes[index],
                            300
                        )
                    } catch (e: Exception) {
                        continue
                    }

                val imagen = ImageView(this)

                imagen.setImageBitmap(miniatura)
                imagen.scaleType =
                    ImageView.ScaleType.FIT_CENTER

                imagen.layoutParams =
                    LinearLayout.LayoutParams(
                        tamanoMiniatura,
                        tamanoMiniatura
                    )

                val margen =
                    (6 * densidad).toInt()

                val parametros =
                    imagen.layoutParams
                        as LinearLayout.LayoutParams

                parametros.setMargins(
                    margen,
                    0,
                    margen,
                    0
                )

                imagen.setOnClickListener {
                    quitarImagen(index)
                }

                vistas.add(imagen)
            }

            runOnUiThread {
                for (vista in vistas) {
                    contenedorMiniaturas.addView(vista)
                }
            }

        }.start()
    }

    private fun quitarImagen(index: Int) {
        if (
            index < 0 ||
            index >= imagenes.size
        ) {
            return
        }

        imagenes.removeAt(index)

        cargarMiniaturas()
        actualizarInfo()

        if (imagenes.isEmpty()) {
            mensaje("Marca otras imágenes para continuar")
        }
    }

    private fun decodificarImagen(
        uri: Uri,
        dimensionMaxima: Int
    ): Bitmap {
        val opciones =
            BitmapFactory.Options()

        opciones.inJustDecodeBounds = true

        contentResolver
            .openInputStream(uri)
            ?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    opciones
                )
            } ?: throw Exception(
                "No se pudo leer la imagen"
            )

        var ancho = opciones.outWidth
        var alto = opciones.outHeight
        var muestra = 1

        while (
            ancho / 2 >= dimensionMaxima ||
            alto / 2 >= dimensionMaxima
        ) {
            ancho /= 2
            alto /= 2
            muestra *= 2
        }

        if (muestra <= 1 && ancho <= 0) {
            throw Exception(
                "Imagen no válida"
            )
        }

        val opcionesFinales =
            BitmapFactory.Options()

        opcionesFinales.inSampleSize = muestra

        return contentResolver
            .openInputStream(uri)
            ?.use { entrada ->
                BitmapFactory.decodeStream(
                    entrada,
                    null,
                    opcionesFinales
                )
            } ?: throw Exception(
                "No se pudo leer la imagen"
            )
    }

    private fun crearPdf() {
        if (guardando) {
            return
        }

        if (imagenes.isEmpty()) {
            mensaje("No hay imágenes")
            return
        }

        guardando = true

        val tamano = tamanoSeleccionado()

        Thread {
            try {
                val uri =
                    crearPdfConImagenes(tamano)

                runOnUiThread {
                    mostrarGuardado(uri)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    mensaje("Error:\n${e.message}")
                }

            } finally {
                guardando = false
            }
        }.start()
    }

    private fun crearPdfConImagenes(
        tamano: TamanoPagina
    ): Uri {
        val documento = PdfDocument()

        try {

            for (uri in imagenes) {

                val bitmap =
                    decodificarImagen(
                        uri,
                        MAX_DIMENCION
                    )

                val (anchoPagina, altoPagina) =
                    dimensionesPagina(
                        tamano,
                        bitmap
                    )

                val info =
                    PdfDocument.PageInfo.Builder(
                        anchoPagina,
                        altoPagina,
                        1
                    ).create()

                val paginaPdf =
                    documento.startPage(info)

                val canvas =
                    paginaPdf.canvas

                dibujarImagenEnPagina(
                    canvas,
                    bitmap,
                    anchoPagina,
                    altoPagina
                )

                documento.finishPage(paginaPdf)

                bitmap.recycle()
            }

            return guardarDocumento(documento)

        } finally {
            documento.close()
        }
    }

    private fun dimensionesPagina(
        tamano: TamanoPagina,
        bitmap: Bitmap
    ): Pair<Int, Int> {
        return when (tamano) {
            TamanoPagina.SEGUN_IMAGEN ->
                Pair(
                    bitmap.width,
                    bitmap.height
                )

            TamanoPagina.A4_VERTICAL ->
                Pair(
                    ANCHO_A4,
                    ALTO_A4
                )

            TamanoPagina.A4_HORIZONTAL ->
                Pair(
                    ALTO_A4,
                    ANCHO_A4
                )
        }
    }

    private fun dibujarImagenEnPagina(
        canvas: android.graphics.Canvas,
        bitmap: Bitmap,
        anchoPagina: Int,
        altoPagina: Int
    ) {
        val anchoUtil =
            anchoPagina -
                2 * MARGEN
        val altoUtil =
            altoPagina -
                2 * MARGEN

        val escala =
            min(
                anchoUtil / bitmap.width.toFloat(),
                altoUtil / bitmap.height.toFloat()
            )

        val anchoDibujo =
            bitmap.width * escala
        val altoDibujo =
            bitmap.height * escala

        val izquierda =
            (anchoPagina - anchoDibujo) / 2f
        val arriba =
            (altoPagina - altoDibujo) / 2f

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                izquierda,
                arriba,
                izquierda + anchoDibujo,
                arriba + altoDibujo
            ),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun guardarDocumento(
        documento: PdfDocument
    ): Uri {
        val nombre =
            "pdf_imagenes_" +
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
            .setTitle("PDF creado")
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

    private fun actualizarInfo() {
        txtInfo.text =
            "${imagenes.size} imágenes"
    }

    private fun mensaje(texto: String) {
        Toast.makeText(
            this,
            texto,
            Toast.LENGTH_SHORT
        ).show()
    }
}