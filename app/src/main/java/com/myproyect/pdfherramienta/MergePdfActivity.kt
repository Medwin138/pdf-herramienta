package com.myproyect.pdfherramienta

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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

        btnVolver.setOnClickListener {
            finish()
        }

        btnUnir.setOnClickListener {
            unirPdf()
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

        mostrarLista()
    }

    private fun mostrarLista() {
        listaArchivos.removeAllViews()

        txtInfo.text =
            "${pdfs.size} archivos"

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