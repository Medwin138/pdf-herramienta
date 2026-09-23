package com.myproyect.pdfherramienta

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

object DialogoGuardarComo {

    private var visible = false

    fun mostrar(
        actividad: AppCompatActivity,
        inicial: String,
        alGuardar: (nombre: String) -> Unit
    ) {
        if (visible) {
            return
        }

        val campo = EditText(actividad)
        campo.hint = "Nombre del PDF"
        campo.setText(inicial)
        campo.inputType =
            InputType.TYPE_CLASS_TEXT
        campo.selectAll()

        val densidad =
            actividad.resources.displayMetrics.density

        val margen =
            (24 * densidad).toInt()

        val parametros =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        parametros.leftMargin = margen
        parametros.rightMargin = margen

        campo.layoutParams = parametros

        val dialogo =
            AlertDialog.Builder(actividad)
                .setTitle("Guardar como")
                .setView(campo)
                .setPositiveButton("Guardar") { _, _ ->
                    val nombre =
                        sanitizar(
                            campo.text.toString()
                        )

                    alGuardar(nombre)
                }
                .setNegativeButton(
                    "Cancelar",
                    null
                )
                .create()

        dialogo.setOnDismissListener {
            visible = false
        }

        visible = true
        dialogo.show()
    }

    fun sanitizar(texto: String): String {
        var nombre =
            texto
                .trim()
                .replace(
                    Regex("[\\\\/:*?\"<>|]"),
                    ""
                )
                .trim()

        if (
            nombre.endsWith(".pdf", ignoreCase = true)
        ) {
            nombre =
                nombre
                    .substring(0, nombre.length - 4)
                    .trim()
        }

        if (nombre.isEmpty()) {
            nombre =
                "documento_" +
                    System.currentTimeMillis()
        }

        return "$nombre.pdf"
    }
}