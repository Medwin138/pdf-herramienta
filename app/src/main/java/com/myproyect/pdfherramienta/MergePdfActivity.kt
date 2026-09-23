package com.myproyect.pdfherramienta

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import java.io.File
import java.io.FileOutputStream

class MergePdfActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PDFS = "pdfs"

        private const val LIMITE = 3
        private const val RESOLUCION = 1600
        private const val SELECCIONAR_PDFS_MAS = 300
    }

    private val pdfs = mutableListOf<Uri>()

    private lateinit var listaArchivos: LinearLayout
    private lateinit var txtInfo: TextView

    private var guardando = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_merge_pdf)

        listaArchivos =
            findViewById(R.id.listaArchivos)
        txtInfo = findViewById(R.id.txtInfo)

        val btnVolver =
            findViewById<Button>(R.id.btnVolver)
        val btnUnir =
            findViewById<Button>(R.id.btnUnir)
        val btnAgregarPdf =
            findViewById<Button>(R.id.btnAgregarPdf)
        val btnVistaPrevia =
            findViewById<Button>(R.id.btnVistaPrevia)

        btnVolver.setOnClickListener {
            finish()
        }

        btnUnir.setOnClickListener {
            unirPdf()
        }

        btnAgregarPdf.setOnClickListener {
            agregarPdfs()
        }

        btnVistaPrevia.setOnClickListener {
            mostrarVistaPrevia()
        }

        val uris =
            intent.getStringArrayListExtra(
                EXTRA_PDFS
            )

        if (uris.isNullOrEmpty()) {
            mensaje("No se recibieron archivos.")
            finish()
            return
        }

        for (texto in uris) {
            pdfs.add(Uri.parse(texto))
        }

        if (pdfs.size > LIMITE) {
            while (pdfs.size > LIMITE) {
                pdfs.removeAt(pdfs.size - 1)
            }
            mensaje(
                "Solo se admiten $LIMITE PDFs por unión"
            )
        }

        mostrarLista()
    }

    private fun mostrarLista() {
        listaArchivos.removeAllViews()

        txtInfo.text =
            "${pdfs.size} / $LIMITE archivos"

        if (pdfs.isEmpty()) {
            return
        }

        val densidad =
            resources.displayMetrics.density

        val margenVertical =
            (6 * densidad).toInt()

        for (index in pdfs.indices) {

            val boton = Button(this)

            boton.text =
                "${index + 1}. ${nombreDe(pdfs[index])}"

            boton.isAllCaps = false
            boton.maxLines = 1
            boton.gravity = Gravity.CENTER

            val parametros =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

            parametros.topMargin =
                margenVertical

            boton.setOnClickListener {
                pdfs.removeAt(index)
                mostrarLista()

                if (pdfs.isEmpty()) {
                    mensaje(
                        "Selecciona al menos un PDF"
                    )
                }
            }

            listaArchivos.addView(
                boton,
                parametros
            )
        }
    }

    private fun nombreDe(uri: Uri): String {
        var nombre: String? = null

        contentResolver
            .query(
                uri,
                null,
                null,
                null,
                null
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columna =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )

                    if (columna >= 0) {
                        nombre =
                            cursor.getString(
                                columna
                            )
                    }
                }
            }

        nombre?.let {
            return it
        }

        return uri.lastPathSegment
            ?: "PDF ${pdfs.size}"
    }

    private fun agregarPdfs() {
        if (pdfs.size >= LIMITE) {
            mensaje(
                "Máximo $LIMITE PDFs por unión"
            )
            return
        }

        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "application/pdf"

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
            SELECCIONAR_PDFS_MAS
        )
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
            requestCode == SELECCIONAR_PDFS_MAS &&
            resultCode == RESULT_OK
        ) {
            val nuevos =
                mutableListOf<Uri>()

            data?.clipData?.let { clip ->
                for (
                    i in 0 until clip.itemCount
                ) {
                    clip.getItemAt(i).uri
                        ?.let { nuevos.add(it) }
                }
            }

            data?.data?.let { nuevos.add(it) }

            if (nuevos.isNotEmpty()) {

                for (uri in nuevos) {
                    try {
                        contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                    } catch (ignorado: SecurityException) {
                    }
                }

                for (uri in nuevos) {
                    if (pdfs.size >= LIMITE) {
                        mensaje(
                            "Máximo $LIMITE PDFs por unión; " +
                                "los demás se ignoraron"
                        )
                        break
                    }
                    pdfs.add(uri)
                }

                mostrarLista()
            }
        }
    }

    private fun mostrarVistaPrevia() {
        if (pdfs.isEmpty()) {
            mensaje(
                "Selecciona al menos un PDF"
            )
            return
        }

        val densidad =
            resources.displayMetrics.density

        val contenedor =
            LinearLayout(this)

        contenedor.orientation =
            LinearLayout.VERTICAL

        val preview =
            EditorCanvasView(this)

        preview.setModo(
            EditorCanvasView.Modo.MOVER
        )

        val titulo =
            TextView(this).apply {
                text = "Cargando vista previa…"
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 8)
            }

        contenedor.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (480 * densidad).toInt()
            )
        )

        contenedor.addView(
            titulo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialogo =
            AlertDialog.Builder(this)
                .setTitle("Vista previa (en este orden)")
                .setView(contenedor)
                .setNegativeButton("Cerrar", null)
                .create()

        dialogo.setCanceledOnTouchOutside(false)

        preview.alCambiarPaginaActual = {
            pagina ->
            titulo.text =
                "Página ${pagina + 1}"
        }

        dialogo.setOnDismissListener {
            preview.reciclarPaginas()
        }

        dialogo.show()

        Thread {

            val bitmaps =
                mutableListOf<Bitmap>()

            try {

                for (
                    index in pdfs.indices
                ) {
                    val archivo =
                        copiarPdfTemporal(
                            pdfs[index],
                            100 + index
                        )

                    val descriptor =
                        ParcelFileDescriptor.open(
                            archivo,
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )

                    val pdf =
                        PdfRenderer(descriptor)

                    for (i in 0 until pdf.pageCount) {

                        val pagina =
                            pdf.openPage(i)

                        val escala =
                            RESOLUCION.toFloat() /
                                pagina.width.toFloat()

                        val alto =
                            (pagina.height * escala)
                                .toInt()

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
                            PdfRenderer.Page
                                .RENDER_MODE_FOR_DISPLAY
                        )

                        pagina.close()

                        bitmaps.add(bitmap)
                    }

                    pdf.close()
                    descriptor.close()
                    archivo.delete()
                }

                runOnUiThread {

                    val total =
                        bitmaps.size

                    preview.setPaginas(
                        bitmaps,
                        emptyMap()
                    )

                    preview.irAPagina(0)

                    titulo.text =
                        if (total <= 0) {
                            "Sin páginas"
                        } else {
                            "Página 1 de $total. " +
                                "Desliza para navegar"
                        }
                }

            } catch (e: Exception) {

                for (bitmap in bitmaps) {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }

                runOnUiThread {
                    dialogo.dismiss()
                    mensaje(
                        "No se pudo previsualizar:\n" +
                            e.message
                    )
                }
            }

        }.start()
    }

    private fun unirPdf() {
        if (guardando) {
            return
        }

        if (pdfs.size < 2) {
            mensaje(
                "Selecciona al menos 2 PDFs"
            )
            return
        }

        guardando = true

        Thread {
            try {
                val uri = crearPdfUnido()

                runOnUiThread {
                    mostrarGuardado(uri)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    mensaje("Error al unir:\n${e.message}")
                }

            } finally {
                guardando = false
            }
        }.start()
    }

    private fun crearPdfUnido(): Uri {
        val archivos = mutableListOf<File>()

        try {

            for (
                index in pdfs.indices
            ) {
                archivos.add(
                    copiarPdfTemporal(
                        pdfs[index],
                        index
                    )
                )
            }

            val salida =
                File(
                    cacheDir,
                    "pdf_unido_" +
                        System.currentTimeMillis() +
                        ".pdf"
                )

            val nombre =
                "pdf_unido_" +
                    System.currentTimeMillis() +
                    ".pdf"

            val merger =
                PDFMergerUtility()

            merger.setDestinationFileName(
                salida.absolutePath
            )

            for (archivo in archivos) {
                merger.addSource(archivo)
            }

            merger.mergeDocuments(
                MemoryUsageSetting
                    .setupMainMemoryOnly()
            )

            return guardarArchivo(
                salida,
                nombre
            )

        } finally {
            for (archivo in archivos) {
                if (archivo.exists()) {
                    archivo.delete()
                }
            }
        }
    }

    private fun copiarPdfTemporal(
        uri: Uri,
        index: Int
    ): File {
        val archivo =
            File(
                cacheDir,
                "fuente_$index.pdf"
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

    private fun guardarArchivo(
        archivo: File,
        nombre: String
    ): Uri {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            guardarEnDescargas(
                archivo,
                nombre
            )
        } else {
            guardarEnArchivoApp(
                archivo,
                nombre
            )
        }
    }

    private fun guardarEnDescargas(
        archivo: File,
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

                archivo.inputStream()
                    .use { entrada ->
                        entrada.copyTo(salida)
                    }
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
        archivo: File,
        nombre: String
    ): Uri {
        val directorio =
            getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: filesDir

        val destino =
            File(
                directorio,
                nombre
            )

        archivo.copyTo(
            destino,
            true
        )

        return FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            destino
        )
    }

    private fun mostrarGuardado(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("PDF unido")
            .setMessage("Los PDFs se unieron y se guardaron en Descargas.")
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

    private fun mensaje(texto: String) {
        Toast.makeText(
            this,
            texto,
            Toast.LENGTH_SHORT
        ).show()
    }
}