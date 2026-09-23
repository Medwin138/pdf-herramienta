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

        private const val SELECCIONAR_MAS_IMAGENES = 200
        private const val CAPTURAR_FOTO = 201

        private const val ANCHO_A4 = 595
        private const val ALTO_A4 = 842
        private const val MARGEN = 24f
    }

    private val imagenes = mutableListOf<Uri>()

    private var fotoTemporal: Uri? = null

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
        val btnTomarFoto =
            findViewById<Button>(R.id.btnTomarFoto)
        val btnAgregarGaleria =
            findViewById<Button>(R.id.btnAgregarGaleria)

        btnVolver.setOnClickListener {
            finish()
        }

        btnCrearPdf.setOnClickListener {
            if (guardando) {
                return@setOnClickListener
            }

            if (imagenes.isEmpty()) {
                mensaje("No hay imágenes")
                return@setOnClickListener
            }

            DialogoGuardarComo.mostrar(
                this,
                "documento"
            ) { nombre ->
                crearPdf(nombre)
            }
        }

        btnTomarFoto.setOnClickListener {
            tomarFoto()
        }

        btnAgregarGaleria.setOnClickListener {
            elegirMasGaleria()
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
                    mostrarVistaPrevia(index)
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

    private fun tomarFoto() {
        try {
            val directorio =
                getExternalFilesDir(
                    Environment.DIRECTORY_PICTURES
                ) ?: filesDir

            directorio.mkdirs()

            val archivo =
                File(
                    directorio,
                    "foto_" +
                        System.currentTimeMillis() +
                        ".jpg"
                )

            val uriFoto =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    archivo
                )

            fotoTemporal = uriFoto

            val intent =
                Intent(
                    MediaStore.ACTION_IMAGE_CAPTURE
                )

            intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                uriFoto
            )

            intent.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            startActivityForResult(
                intent,
                CAPTURAR_FOTO
            )
        } catch (e: Exception) {
            mensaje("No se pudo abrir la cámara")
        }
    }

    private fun elegirMasGaleria() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "image/*"

        intent.putExtra(
            Intent.EXTRA_ALLOW_MULTIPLE,
            true
        )

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        intent.addFlags(
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )

        startActivityForResult(
            intent,
            SELECCIONAR_MAS_IMAGENES
        )
    }

    private fun mostrarVistaPrevia(
        index: Int
    ) {
        if (
            index < 0 ||
            index >= imagenes.size
        ) {
            return
        }

        val uri = imagenes[index]

        Thread {
            val completa =
                try {
                    decodificarImagen(
                        uri,
                        1600
                    )
                } catch (e: Exception) {
                    null
                }

            runOnUiThread {
                if (completa == null) {
                    mensaje(
                        "No se pudo leer la imagen"
                    )
                    return@runOnUiThread
                }

                val imagen =
                    ImageView(this)

                imagen.setImageBitmap(completa)
                imagen.scaleType =
                    ImageView.ScaleType.FIT_CENTER

                val densidad =
                    resources.displayMetrics.density

                val relleno =
                    (12 * densidad).toInt()

                imagen.setPadding(
                    relleno,
                    relleno,
                    relleno,
                    relleno
                )

                val dialogo =
                    AlertDialog.Builder(this)
                        .setTitle(
                            "Imagen ${index + 1} " +
                                "de ${imagenes.size}"
                        )
                        .setView(imagen)
                        .setPositiveButton(
                            "Quitar"
                        ) { _, _ ->
                            quitarImagen(index)
                        }
                        .setNegativeButton(
                            "Cerrar",
                            null
                        )
                        .create()

                dialogo.setOnDismissListener {
                    if (!completa.isRecycled) {
                        completa.recycle()
                    }
                }

                dialogo.show()
            }
        }.start()
    }

    @Deprecated("Compatible con nuestro SDK")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == CAPTURAR_FOTO &&
            resultCode == RESULT_OK
        ) {
            val uri = fotoTemporal

            if (uri != null) {
                imagenes.add(uri)
                cargarMiniaturas()
                actualizarInfo()
            } else {
                mensaje("No se recibió la foto")
            }

            fotoTemporal = null
            return
        }

        if (
            requestCode == SELECCIONAR_MAS_IMAGENES &&
            resultCode == RESULT_OK
        ) {
            val nuevas =
                mutableListOf<Uri>()

            data?.clipData?.let { clip ->
                for (
                    i in 0 until clip.itemCount
                ) {
                    clip.getItemAt(i).uri
                        ?.let { nuevas.add(it) }
                }
            }

            data?.data?.let { nuevas.add(it) }

            if (nuevas.isNotEmpty()) {

                for (uri in nuevas) {
                    try {
                        contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                    } catch (ignorado: SecurityException) {
                    }
                }

                imagenes.addAll(nuevas)
                cargarMiniaturas()
                actualizarInfo()
            }
        }
    }

private fun decodificarImagen(
        uri: Uri,
        dimensionMaxima: Int
    ): Bitmap {
        val opciones =
            BitmapFactory.Options()

        opciones.inJustDecodeBounds = true

        val entrada =
            contentResolver
                .openInputStream(uri)
                ?: throw Exception(
                    "No se pudo leer la imagen"
                )

        entrada.use {
            BitmapFactory.decodeStream(
                it,
                null,
                opciones
            )
        }

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
            ?.use { entradaFinal ->
                BitmapFactory.decodeStream(
                    entradaFinal,
                    null,
                    opcionesFinales
                )
            } ?: throw Exception(
            "No se pudo leer la imagen"
        )
    }

    private fun crearPdf(nombre: String) {
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
                    crearPdfConImagenes(tamano, nombre)

                runOnUiThread {
                    mostrarGuardado(uri, nombre)
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
        tamano: TamanoPagina,
        nombre: String
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

            return guardarDocumento(documento, nombre)

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

    private fun mostrarGuardado(uri: Uri, nombre: String) {
        AlertDialog.Builder(this)
            .setTitle("PDF creado")
            .setMessage(
                "El PDF se guardó en Descargas como:\n$nombre"
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