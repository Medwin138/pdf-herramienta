package com.myproyect.pdfherramienta

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Extrae el texto de cada página del PDF
 * con sus posiciones, fuente y tamaño,
 * usando PDFBox.
 *
 * Las coordenadas resultantes están en el
 * sistema de la página renderizada por el
 * editor: píxeles con origen arriba-izquierda
 * y escala = resolución / ancho del PDF (pts).
 */
object TextoReconocimiento {

    fun reconocerPagina(
        pdf: File,
        indicePagina: Int,
        anchoPaginaPts: Int,
        altoPaginaPts: Int,
        resolucion: Int
    ): List<TextoReconocido> {

        val documento = try {
            PDDocument.load(pdf)
        } catch (e: Exception) {
            return emptyList()
        }

        if (
            indicePagina < 0 ||
            indicePagina >= documento.numberOfPages
        ) {
            documento.close()
            return emptyList()
        }

        val todos =
            mutableListOf<PosicionCaracter>()

        try {

            val stripper =
                object : PDFTextStripper() {

                    init {
                        setStartPage(indicePagina + 1)
                        setEndPage(indicePagina + 1)
                        sortByPosition = true
                    }

                    override fun writeString(
                        texto: String,
                        posiciones: List<TextPosition>
                    ) {
                        for (posicion in posiciones) {

                            val textoCaracter =
                                posicion.unicode
                                    ?.trim()
                                    ?: ""

                            if (
                                textoCaracter.isEmpty()
                            ) {
                                continue
                            }

                            todos.add(
                                PosicionCaracter(
                                    textoCaracter,
                                    posicion.xDirAdj,
                                    posicion.yDirAdj,
                                    posicion.widthDirAdj,
                                    posicion.heightDir,
                                    posicion.font?.name
                                        ?: "",
                                    posicion.fontSizeInPt
                                )
                            )
                        }
                    }
                }

            stripper.getText(documento)

        } catch (e: Exception) {
            documento.close()
            return emptyList()
        }

        documento.close()

        if (todos.isEmpty()) {
            return emptyList()
        }

        val escala =
            resolucion.toFloat() /
                anchoPaginaPts.toFloat()

        val lineas =
            agruparLineas(todos)

        val resultado =
            mutableListOf<TextoReconocido>()

        for (linea in lineas) {

            if (linea.isEmpty()) {
                continue
            }

            val texto =
                linea.joinToString("") {
                    it.caracter
                }

            var xMin = Float.MAX_VALUE
            var yMax = Float.MIN_VALUE
            var xMax = Float.MIN_VALUE
            var yMin = Float.MAX_VALUE

            var fuente = ""

            for (caracter in linea) {
                xMin = min(xMin, caracter.x)
                xMax = max(
                    xMax,
                    caracter.x + caracter.ancho
                )
                yMin = min(yMin, caracter.y)
                yMax = max(yMax, caracter.y)

                if (fuente.isEmpty()) {
                    fuente = caracter.fuente
                }
            }

            val tamano =
                linea.maxBy {
                    it.anchoAlto
                }?.tamanoPuntos
                    ?: 12f

            resultado.add(
                TextoReconocido(
                    texto,
                    xMin * escala,
                    (altoPaginaPts - yMax) * escala,
                    xMax * escala,
                    (altoPaginaPts - yMin) * escala,
                    fuente,
                    tamano
                )
            )
        }

        return resultado
    }

    fun fuenteElegida(nombre: String): String {

        val nombreLimpio =
            nombre.toLowerCase()

        if (
            nombreLimpio.contains("script") ||
            nombreLimpio.contains("dancing") ||
            nombreLimpio.contains("brush") ||
            nombreLimpio.contains("hand") ||
            nombreLimpio.contains("cursive")
        ) {
            return "dancing"
        }

        if (nombreLimpio.contains("oswald")) {
            return "oswald"
        }

        if (nombreLimpio.contains("bebas")) {
            return "bebas"
        }

        if (nombreLimpio.contains("playfair")) {
            return "playfair"
        }

        if (nombreLimpio.contains("raleway")) {
            return "raleway"
        }

        if (
            nombreLimpio.contains("courier") ||
            nombreLimpio.contains("mono") ||
            nombreLimpio.contains("consolas")
        ) {
            return "mono"
        }

        if (
            nombreLimpio.contains("times") ||
            nombreLimpio.contains("georgia") ||
            nombreLimpio.contains("garamond") ||
            nombreLimpio.contains("palatino") ||
            nombreLimpio.contains("roman") ||
            nombreLimpio.contains("italic")
        ) {
            return "serif"
        }

        return "sans"
    }

    fun tamanoEnEditor(
        tamanoPuntos: Float,
        anchoPaginaPts: Int,
        resolucion: Int
    ): Int {

        return (
            tamanoPuntos *
                resolucion.toFloat() /
                anchoPaginaPts.toFloat()
            ).toInt().coerceIn(
                12,
                200
            )
    }

    private class PosicionCaracter(
        val caracter: String,
        val x: Float,
        val y: Float,
        val ancho: Float,
        val alto: Float,
        val fuente: String,
        val tamanoPuntos: Float
    ) {

        val anchoAlto: Float
            get() = ancho * alto
    }

    private fun agruparLineas(
        caracteres: List<PosicionCaracter>
    ): List<List<PosicionCaracter>> {

        val restantes =
            caracteres
                .sortedBy { it.y }
                .toMutableList()

        val lineas =
            mutableListOf<List<PosicionCaracter>>()

        while (restantes.isNotEmpty()) {

            val primera =
                restantes.removeAt(0)

            val linea =
                mutableListOf(primera)

            val tolerancia =
                TOLERANCIA_LINEA *
                    primera.tamanoPuntos

            var i = 0

            while (i < restantes.size) {

                val candidato =
                    restantes[i]

                if (
                    abs(
                        candidato.y - primera.y
                    ) <= tolerancia
                ) {
                    linea.add(candidato)
                    restantes.removeAt(i)
                } else {
                    i++
                }
            }

            lineas.add(
                linea.sortedBy {
                    it.x
                }
            )
        }

        return lineas
    }

    private const val TOLERANCIA_LINEA = 2.5f
}