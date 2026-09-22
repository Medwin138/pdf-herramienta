package com.myproyect.pdfherramienta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object FirmaGuardada {

    fun directorio(
        context: Context
    ): File {
        val dir = File(
            context.filesDir,
            "firmas"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun guardar(
        context: Context,
        bitmap: Bitmap
    ): File {
        val archivo = File(
            directorio(context),
            "firma_" +
                System.currentTimeMillis() +
                ".png"
        )

        FileOutputStream(archivo).use {
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }

        return archivo
    }

    fun listar(
        context: Context
    ): List<File> {
        return directorio(context)
            .listFiles()
            ?.filter {
                it.extension == "png"
            }
            ?.sortedByDescending {
                it.lastModified()
            }
            ?: emptyList()
    }

    fun cargar(
        archivo: File
    ): Bitmap? {
        return BitmapFactory.decodeFile(
            archivo.absolutePath
        )
    }

    fun eliminar(
        archivo: File
    ) {
        archivo.delete()
    }

    fun vaciar(
        context: Context
    ) {
        listar(context).forEach {
            it.delete()
        }
    }
}