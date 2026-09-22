package com.myproyect.pdfherramienta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SELECCIONAR_PDF = 100
        private const val SELECCIONAR_PDF_EDITAR = 101
        private const val SELECCIONAR_PDF_FIRMAR = 102
        private const val SELECCIONAR_IMAGENES = 103
        private const val SELECCIONAR_PDFS_UNIR = 104
        private const val SELECCIONAR_PDF_COMPARTIR = 105
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnVerPdf = findViewById<Button>(R.id.btnVerPdf)
        val btnEditarPdf = findViewById<Button>(R.id.btnEditarPdf)
        val btnFirmarPdf = findViewById<Button>(R.id.btnFirmarPdf)
        val btnImagenPdf = findViewById<Button>(R.id.btnImagenPdf)
        val btnUnirPdf = findViewById<Button>(R.id.btnUnirPdf)
        val btnCompartirPdf = findViewById<Button>(R.id.btnCompartirPdf)

        btnVerPdf.setOnClickListener {
            seleccionarPdf()
        }

        btnEditarPdf.setOnClickListener {
            seleccionarPdfParaEditar()
        }

        btnFirmarPdf.setOnClickListener {
            seleccionarPdfParaFirmar()
        }

        btnImagenPdf.setOnClickListener {
            seleccionarImagenes()
        }

        btnUnirPdf.setOnClickListener {
            seleccionarPdfsParaUnir()
        }

        btnCompartirPdf.setOnClickListener {
            seleccionarPdfParaCompartir()
        }
    }

    private fun seleccionarPdf() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "application/pdf"

        startActivityForResult(
            intent,
            SELECCIONAR_PDF
        )
    }

    private fun seleccionarPdfParaEditar() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "application/pdf"

        startActivityForResult(
            intent,
            SELECCIONAR_PDF_EDITAR
        )
    }

    private fun seleccionarPdfParaFirmar() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "application/pdf"

        startActivityForResult(
            intent,
            SELECCIONAR_PDF_FIRMAR
        )
    }

    private fun seleccionarImagenes() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

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
            SELECCIONAR_IMAGENES
        )
    }

    private fun seleccionarPdfsParaUnir() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

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
            SELECCIONAR_PDFS_UNIR
        )
    }

    private fun seleccionarPdfParaCompartir() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "application/pdf"

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        intent.addFlags(
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )

        startActivityForResult(
            intent,
            SELECCIONAR_PDF_COMPARTIR
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
            requestCode == SELECCIONAR_PDF &&
            resultCode == RESULT_OK
        ) {

            val uri = data?.data

            if (uri != null) {

                val intent = Intent(
                    this,
                    PdfViewerActivity::class.java
                )

                intent.data = uri

                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                startActivity(intent)
            }
        }

        if (
            requestCode == SELECCIONAR_PDF_EDITAR &&
            resultCode == RESULT_OK
        ) {

            val uri = data?.data

            if (uri != null) {

                val intent = Intent(
                    this,
                    PdfEditorActivity::class.java
                )

                intent.data = uri

                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                startActivity(intent)
            }
        }

        if (
            requestCode == SELECCIONAR_PDF_FIRMAR &&
            resultCode == RESULT_OK
        ) {

            val uri = data?.data

            if (uri != null) {

                val intent = Intent(
                    this,
                    SignActivity::class.java
                )

                intent.data = uri

                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                startActivity(intent)
            }
        }

        if (
            requestCode == SELECCIONAR_IMAGENES &&
            resultCode == RESULT_OK
        ) {

            val uris = mutableListOf<Uri>()

            data?.clipData?.let { clip ->
                for (
                    i in 0 until clip.itemCount
                ) {
                    clip.getItemAt(i).uri
                        ?.let { uris.add(it) }
                }
            }

            data?.data?.let { uris.add(it) }

            if (uris.isNotEmpty()) {

                for (uri in uris) {
                    try {
                        contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                    } catch (ignorado: SecurityException) {
                    }
                }

                val textos =
                    uris
                        .map { it.toString() }
                        .toCollection(ArrayList())

                val intent = Intent(
                    this,
                    ImagesToPdfActivity::class.java
                )

                intent.putStringArrayListExtra(
                    ImagesToPdfActivity.EXTRA_IMAGENES,
                    textos
                )

                startActivity(intent)
            }
        }

        if (
            requestCode == SELECCIONAR_PDFS_UNIR &&
            resultCode == RESULT_OK
        ) {

            val uris = mutableListOf<Uri>()

            data?.clipData?.let { clip ->
                for (
                    i in 0 until clip.itemCount
                ) {
                    clip.getItemAt(i).uri
                        ?.let { uris.add(it) }
                }
            }

            data?.data?.let { uris.add(it) }

            if (uris.isNotEmpty()) {

                for (uri in uris) {
                    try {
                        contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                    } catch (ignorado: SecurityException) {
                    }
                }

                val textos =
                    uris
                        .map { it.toString() }
                        .toCollection(ArrayList())

                val intent = Intent(
                    this,
                    MergePdfActivity::class.java
                )

                intent.putStringArrayListExtra(
                    MergePdfActivity.EXTRA_PDFS,
                    textos
                )

                startActivity(intent)
            }
        }

        if (
            requestCode == SELECCIONAR_PDF_COMPARTIR &&
            resultCode == RESULT_OK
        ) {

            val uri = data?.data

            if (uri != null) {

                try {
                    contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                } catch (ignorado: SecurityException) {
                }

                compartirPdf(uri)
            }
        }
    }

    private fun compartirPdf(uri: Uri) {

        val intent = Intent(Intent.ACTION_SEND)

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