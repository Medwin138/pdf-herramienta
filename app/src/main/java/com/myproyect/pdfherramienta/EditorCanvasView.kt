package com.myproyect.pdfherramienta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

interface Anotacion {
    fun dibujar(canvas: Canvas)
}

/**
 * Forma que se puede arrastrar con el dedo
 * cuando está seleccionada.
 */
interface FormaArrastrable {
    fun contienePunto(
        x: Float,
        y: Float
    ): Boolean

    fun mover(
        deltaX: Float,
        deltaY: Float
    )
}

class Trazo(
    val camino: Path,
    val pintura: Paint
) : Anotacion {

    override fun dibujar(canvas: Canvas) {
        canvas.drawPath(camino, pintura)
    }
}

class RectanguloBlanco(
    val izquierda: Float,
    val superior: Float,
    val derecha: Float,
    val inferior: Float
) : Anotacion {

    override fun dibujar(canvas: Canvas) {
        canvas.drawRect(
            izquierda,
            superior,
            derecha,
            inferior,
            pinturaBlanco
        )
    }
}

private val pinturaBlanco =
    Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

/**
 * Texto extraído del PDF mediante PDFBox.
 *
 * Las coordenadas están en el sistema de
 * coordenadas de la página renderizada
 * (píxeles con origen arriba-izquierda).
 */
class TextoReconocido(
    val texto: String,
    val izquierda: Float,
    val superior: Float,
    val derecha: Float,
    val inferior: Float,
    val nombreFuente: String,
    val tamanoFuente: Float
)

class LineaForma(
    var inicioX: Float,
    var inicioY: Float,
    var finX: Float,
    var finY: Float,
    val pintura: Paint
) : Anotacion, FormaArrastrable {

    override fun dibujar(canvas: Canvas) {
        canvas.drawLine(
            inicioX,
            inicioY,
            finX,
            finY,
            pintura
        )
    }

    override fun contienePunto(
        x: Float,
        y: Float
    ): Boolean {
        val px = finX - inicioX
        val py = finY - inicioY
        val ladoCuadrado =
            px * px + py * py
        val t =
            if (ladoCuadrado == 0f) {
                0f
            } else {
                (
                    (x - inicioX) * px +
                        (y - inicioY) * py
                    ) / ladoCuadrado
            }.coerceIn(0f, 1f)
        val cx = inicioX + t * px
        val cy = inicioY + t * py
        val distanciaX = x - cx
        val distanciaY = y - cy
        return (
            distanciaX * distanciaX +
                distanciaY * distanciaY
            ) <= 24f * 24f
    }

    override fun mover(
        deltaX: Float,
        deltaY: Float
    ) {
        inicioX += deltaX
        inicioY += deltaY
        finX += deltaX
        finY += deltaY
    }
}

class FlechaForma(
    var inicioX: Float,
    var inicioY: Float,
    var finX: Float,
    var finY: Float,
    val pintura: Paint
) : Anotacion, FormaArrastrable {

    override fun dibujar(canvas: Canvas) {
        canvas.drawLine(
            inicioX,
            inicioY,
            finX,
            finY,
            pintura
        )

        val angulo =
            Math.atan2(
                (finY - inicioY).toDouble(),
                (finX - inicioX).toDouble()
            ).toFloat()

        val longitud =
            30f

        canvas.drawLine(
            finX,
            finY,
            finX - longitud *
                Math.cos((angulo - 0.4f).toDouble())
                .toFloat(),
            finY - longitud *
                Math.sin((angulo - 0.4f).toDouble())
                .toFloat(),
            pintura
        )

        canvas.drawLine(
            finX,
            finY,
            finX - longitud *
                Math.cos((angulo + 0.4f).toDouble())
                .toFloat(),
            finY - longitud *
                Math.sin((angulo + 0.4f).toDouble())
                .toFloat(),
            pintura
        )
    }

    override fun contienePunto(
        x: Float,
        y: Float
    ): Boolean {
        val px = finX - inicioX
        val py = finY - inicioY
        val ladoCuadrado =
            px * px + py * py
        val t =
            if (ladoCuadrado == 0f) {
                0f
            } else {
                (
                    (x - inicioX) * px +
                        (y - inicioY) * py
                    ) / ladoCuadrado
            }.coerceIn(0f, 1f)
        val cx = inicioX + t * px
        val cy = inicioY + t * py
        val distanciaX = x - cx
        val distanciaY = y - cy
        return (
            distanciaX * distanciaX +
                distanciaY * distanciaY
            ) <= 40f * 40f
    }

    override fun mover(
        deltaX: Float,
        deltaY: Float
    ) {
        inicioX += deltaX
        inicioY += deltaY
        finX += deltaX
        finY += deltaY
    }
}

class RectanguloForma(
    var izquierda: Float,
    var superior: Float,
    var derecha: Float,
    var inferior: Float,
    val pintura: Paint
) : Anotacion, FormaArrastrable {

    override fun dibujar(canvas: Canvas) {
        canvas.drawRect(
            izquierda,
            superior,
            derecha,
            inferior,
            pintura
        )
    }

    override fun contienePunto(
        x: Float,
        y: Float
    ): Boolean {
        val izq = min(izquierda, derecha) - 20f
        val der = max(izquierda, derecha) + 20f
        val sup = min(superior, inferior) - 20f
        val inf = max(superior, inferior) + 20f
        return x >= izq && x <= der && y >= sup && y <= inf
    }

    override fun mover(
        deltaX: Float,
        deltaY: Float
    ) {
        izquierda += deltaX
        derecha += deltaX
        superior += deltaY
        inferior += deltaY
    }
}

class ElipseForma(
    var izquierda: Float,
    var superior: Float,
    var derecha: Float,
    var inferior: Float,
    val pintura: Paint
) : Anotacion, FormaArrastrable {

    override fun dibujar(canvas: Canvas) {
        canvas.drawOval(
            RectF(
                izquierda,
                superior,
                derecha,
                inferior
            ),
            pintura
        )
    }

    override fun contienePunto(
        x: Float,
        y: Float
    ): Boolean {
        val izq = min(izquierda, derecha) - 20f
        val der = max(izquierda, derecha) + 20f
        val sup = min(superior, inferior) - 20f
        val inf = max(superior, inferior) + 20f
        return x >= izq && x <= der && y >= sup && y <= inf
    }

    override fun mover(
        deltaX: Float,
        deltaY: Float
    ) {
        izquierda += deltaX
        derecha += deltaX
        superior += deltaY
        inferior += deltaY
    }
}

class CajaTextoForma(
    var izquierda: Float,
    var superior: Float,
    var derecha: Float,
    var inferior: Float,
    val pintura: Paint
) : Anotacion, FormaArrastrable {

    override fun dibujar(canvas: Canvas) {
        canvas.drawRect(
            izquierda,
            superior,
            derecha,
            inferior,
            pintura
        )
    }

    override fun contienePunto(
        x: Float,
        y: Float
    ): Boolean {
        val izq = min(izquierda, derecha) - 20f
        val der = max(izquierda, derecha) + 20f
        val sup = min(superior, inferior) - 20f
        val inf = max(superior, inferior) + 20f
        return x >= izq && x <= der && y >= sup && y <= inf
    }

    override fun mover(
        deltaX: Float,
        deltaY: Float
    ) {
        izquierda += deltaX
        derecha += deltaX
        superior += deltaY
        inferior += deltaY
    }
}

class ImagenAnotacion(
    val imagen: Bitmap,
    var x: Float,
    var y: Float,
    var ancho: Float
) : Anotacion, FormaArrastrable {

    val alto: Float
        get() = ancho * imagen.height / imagen.width

    override fun dibujar(canvas: Canvas) {
        canvas.drawBitmap(
            imagen,
            null,
            RectF(
                x,
                y,
                x + ancho,
                y + alto
            ),
            pinturaImagen
        )
    }

    override fun contienePunto(
        xp: Float,
        yp: Float
    ): Boolean {
        val margen = 12f
        return xp >= x - margen &&
            xp <= x + ancho + margen &&
            yp >= y - margen &&
            yp <= y + alto + margen
    }

    override fun mover(
        deltaX: Float,
        deltaY: Float
    ) {
        x += deltaX
        y += deltaY
    }
}

private val pinturaImagen =
    Paint(Paint.FILTER_BITMAP_FLAG)

class TextoAnotacion(
    val texto: String,
    val x: Float,
    val y: Float,
    val pintura: Paint,
    val rotacion: Float = 0f
) : Anotacion {

    override fun dibujar(canvas: Canvas) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotacion)
        canvas.drawText(texto, 0f, 0f, pintura)
        canvas.restore()
    }

    fun dimensiones(): Rect {
        val rect = Rect()
        pintura.getTextBounds(
            texto,
            0,
            texto.length,
            rect
        )
        return rect
    }
}

class EditorCanvasView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Modo {
        DIBUJO,
        TEXTO,
        MOVER,
        BORRAR,
        DETECTAR,
        RESALTAR,
        SUBRAYAR,
        FORMAS
    }

    enum class TipoForma {
        LINEA,
        FLECHA,
        RECTANGULO,
        ELIPSE,
        CAJA_TEXTO
    }

    var alColocarTexto: (() -> Unit)? = null

    var alColocarForma: (() -> Unit)? = null

    var alDetectarTexto: ((Int, TextoReconocido?) -> Unit)? =
        null

    var alCambiarPaginaActual: ((Int) -> Unit)? = null

    var tipoFormaSeleccionada =
        TipoForma.RECTANGULO

    private var ultimaPaginaNotificada =
        -1

    private val paginas =
        mutableListOf<Bitmap>()
    private val offsetsY =
        mutableListOf<Float>()
    private val alturasPaginas =
        mutableListOf<Float>()

    private var modo = Modo.MOVER
    private var textoPendiente: String? = null
    private var pinturaTextoPendiente: Paint? = null
    private var rotacionPendiente = 0f

    private val anotacionesPorPagina =
        mutableMapOf<Int, MutableList<Anotacion>>()
    private val textosReconocidosPorPagina =
        mutableMapOf<Int, List<TextoReconocido>>()

    private val historial =
        mutableListOf<Map<Int, List<Anotacion>>>()
    private val limiteHistorial = 100

    private var paginaTrazoActual =
        mutableMapOf<Int, Trazo>()
    private var indiceSeleccionado = -1
    private var paginaSeleccionada = -1

    private val pinturaBorrar = Paint().apply {
        color = Color.WHITE
        strokeWidth = 30f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val pinturaResaltar = Paint().apply {
        color = Color.parseColor("#80FFEB3B")
        strokeWidth = 36f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val pinturaTrazo = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val pinturaTexto =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 48f
        }

    private val pinturaSeleccion = Paint().apply {
        color = Color.parseColor("#D32F2F")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect =
            DashPathEffect(
                floatArrayOf(8f, 8f),
                0f
            )
    }

    private var escalaBase = 1f
    private var multiplicadorZoom = 1f
    private var desplazamientoX = 0f
    private var desplazamientoY = 0f

    private var ultimoX = 0f
    private var ultimoY = 0f

    private var inicioX = 0f
    private var inicioY = 0f

    private var formaEnProgreso:
        Anotacion? = null
    private var formaPagina = 0

    private val detectorZoom =
        ScaleGestureDetector(
            context,
            object :
                ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScaleBegin(
                    detector: ScaleGestureDetector
                ): Boolean {
                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )
                    return true
                }

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {

                    val anterior =
                        multiplicadorZoom

                    val nuevo =
                        (
                            anterior *
                                detector.scaleFactor
                        ).coerceIn(
                            1f,
                            5f
                        )

                    if (
                        nuevo == anterior
                    ) {
                        return true
                    }

                    val escalaAnterior =
                        escalaBase *
                            anterior

                    multiplicadorZoom =
                        nuevo

                    val focoX =
                        detector.focusX

                    val focoY =
                        detector.focusY

                    val puntoDocX =
                        (
                            focoX -
                                desplazamientoX
                            ) / escalaAnterior

                    val puntoDocY =
                        (
                            focoY -
                                desplazamientoY
                            ) / escalaAnterior

                    desplazamientoX =
                        focoX -
                            puntoDocX *
                            escalaEfectiva()

                    desplazamientoY =
                        focoY -
                            puntoDocY *
                            escalaEfectiva()

                    restringirDesplazamiento()

                    invalidate()

                    return true
                }

                override fun onScaleEnd(
                    detector: ScaleGestureDetector
                ) {
                    parent.requestDisallowInterceptTouchEvent(
                        false
                    )
                }
            }
        )

    fun setPaginas(
        bitmaps: List<Bitmap>,
        anotacionesIniciales:
            Map<Int, List<Anotacion>>
    ) {
        for (bitmap in paginas) {
            bitmap.recycle()
        }
        paginas.clear()
        offsetsY.clear()
        alturasPaginas.clear()
        paginas.addAll(bitmaps)
        anotacionesPorPagina.clear()
        anotacionesIniciales.forEach {
            (indice, lista) ->
            anotacionesPorPagina[indice] =
                lista.toMutableList()
        }
        textosReconocidosPorPagina.clear()
        paginaTrazoActual.clear()
        indiceSeleccionado = -1
        paginaSeleccionada = -1
        multiplicadorZoom = 1f
        ultimaPaginaNotificada = -1

        var acumulado = 0f
        for (bitmap in paginas) {
            offsetsY.add(acumulado)
            alturasPaginas.add(
                bitmap.height.toFloat()
            )
            acumulado += bitmap.height
        }

        calcularEscala()
        invalidate()
    }

    fun cantidadPaginas(): Int {
        return paginas.size
    }

    fun paginaActualVisible(): Int {
        val escala = escalaEfectiva()
        val yCentro =
            (height / 2f - desplazamientoY) / escala
        return paginaEnY(yCentro)
    }

    private fun notificarPaginaActual() {
        val pagina = paginaActualVisible()
        if (pagina != ultimaPaginaNotificada) {
            ultimaPaginaNotificada = pagina
            alCambiarPaginaActual?.invoke(
                pagina
            )
        }
    }

    fun irAPagina(indice: Int) {
        val indiceReal =
            indice.coerceIn(
                0,
                paginas.size - 1
            )

        val escala = escalaEfectiva()
        val offset =
            offsetsY.getOrElse(
                indiceReal
            ) {
                0f
            }

        val alto =
            alturasPaginas.getOrElse(
                indiceReal
            ) {
                0f
            }

        desplazamientoY =
            height / 2f -
                (offset + alto / 2f) *
                escala

        restringirDesplazamiento()
        invalidate()

        alCambiarPaginaActual?.invoke(
            indiceReal
        )
    }

    fun colocarFirma(imagen: Bitmap) {
        if (paginas.isEmpty()) {
            return
        }

        val pagina = paginaActualVisible()
        val anchoPagina =
            paginas[pagina].width.toFloat()
        val altoPagina =
            alturasPaginas.getOrElse(pagina) {
                0f
            }

        val ancho = anchoPagina * 0.30f
        val alto =
            ancho * imagen.height / imagen.width

        val x = (anchoPagina - ancho) / 2f
        val y = (altoPagina - alto) / 2f

        capturarHistorial()

        anotacionesPorPagina
            .getOrPut(pagina) {
                mutableListOf()
            }
            .add(
                ImagenAnotacion(
                    imagen,
                    x,
                    y,
                    ancho
                )
            )

        invalidate()
    }

    fun setModo(modo: Modo) {
        this.modo = modo
        if (
            modo == Modo.DIBUJO ||
            modo == Modo.TEXTO ||
            modo == Modo.RESALTAR ||
            modo == Modo.SUBRAYAR ||
            modo == Modo.FORMAS
        ) {
            indiceSeleccionado = -1
            paginaSeleccionada = -1
        }
    }

    fun setTextosReconocidos(
        pagina: Int,
        textos: List<TextoReconocido>
    ) {
        textosReconocidosPorPagina[pagina] = textos
    }

    fun textoBajoElDedo(
        pagina: Int,
        xPagina: Float,
        yPagina: Float
    ): TextoReconocido? {

        val propios =
            textosReconocidosPorPagina[pagina]

        val candidatos =
            if (propios.isNullOrEmpty()) {
                emptyList()
            } else {
                propios
            }

        var mejor: TextoReconocido? = null
        var mejorDistancia = Float.MAX_VALUE

        for (texto in candidatos) {

            val centroX =
                (texto.izquierda + texto.derecha) / 2f
            val centroY =
                (texto.superior + texto.inferior) / 2f

            val distanciaX =
                xPagina - centroX
            val distanciaY =
                yPagina - centroY

            val distancia =
                distanciaX * distanciaX +
                    distanciaY * distanciaY

            val dentro =
                xPagina >= texto.izquierda &&
                    xPagina <= texto.derecha &&
                    yPagina >= texto.superior &&
                    yPagina <= texto.inferior

            /*
             * Tolerancia: si el dedo cae en el
             * borde o cerca, también valida.
             */
            val radioCero =
                (texto.derecha - texto.izquierda) *
                    (texto.derecha - texto.izquierda) +
                    (texto.superior - texto.inferior) *
                    (texto.superior - texto.inferior)

            if (
                dentro ||
                distancia <=
                radioCero * 4f
            ) {

                if (distancia < mejorDistancia) {
                    mejorDistancia = distancia
                    mejor = texto
                }
            }
        }

        return mejor
    }

    fun setColor(color: Int) {
        pinturaTrazo.color = color
        pinturaTexto.color = color
    }

    fun getColorTexto(): Int {
        return pinturaTexto.color
    }

    fun getTamanoTexto(): Float {
        return pinturaTexto.textSize
    }

    fun setTexto(
        texto: String,
        pintura: Paint,
        grados: Float
    ) {
        textoPendiente = texto
        pinturaTextoPendiente =
            Paint(pintura)
        rotacionPendiente = grados
    }

    fun textoSeleccionado(): TextoAnotacion? {
        val pagina =
            paginaDeAnotacion(indiceSeleccionado)
                ?: return null

        val anotacion =
            anotacionesPorPagina[pagina]
                ?.getOrNull(
                    indiceSeleccionado
                )

        return anotacion as? TextoAnotacion
    }

    fun editarTextoSeleccionado(
        texto: String,
        pintura: Paint
    ) {
        val pagina =
            paginaDeAnotacion(indiceSeleccionado)
                ?: return

        val anotacion =
            anotacionesPorPagina[pagina]
                ?.getOrNull(
                    indiceSeleccionado
                )

        if (anotacion is TextoAnotacion) {
            capturarHistorial()
            val lista =
                anotacionesPorPagina[pagina]
            if (lista != null) {
                lista[indiceSeleccionado] =
                    TextoAnotacion(
                        texto,
                        anotacion.x,
                        anotacion.y,
                        Paint(pintura),
                        anotacion.rotacion
                    )
                invalidate()
            }
        }
    }

    fun girarSeleccionado(
        grados: Float
    ) {
        val pagina =
            paginaDeAnotacion(indiceSeleccionado)
                ?: return

        val anotacion =
            anotacionesPorPagina[pagina]
                ?.getOrNull(
                    indiceSeleccionado
                )

        if (anotacion is TextoAnotacion) {
            capturarHistorial()
            val lista =
                anotacionesPorPagina[pagina]
            if (lista != null) {
                lista[indiceSeleccionado] =
                    TextoAnotacion(
                        anotacion.texto,
                        anotacion.x,
                        anotacion.y,
                        Paint(anotacion.pintura),
                        anotacion.rotacion + grados
                    )
                invalidate()
            }
        }
    }

    fun eliminarSeleccionado() {
        val pagina =
            paginaDeAnotacion(indiceSeleccionado)
                ?: return

        capturarHistorial()

        anotacionesPorPagina[pagina]
            ?.let { lista ->
                if (
                    indiceSeleccionado in lista.indices
                ) {
                    lista.removeAt(indiceSeleccionado)
                }
            }

        indiceSeleccionado = -1
        paginaSeleccionada = -1
        invalidate()
    }

    fun deshacer() {
        if (historial.isEmpty()) {
            return
        }

        val anterior =
            historial.removeAt(historial.size - 1)

        anotacionesPorPagina.clear()
        anterior.forEach {
            (pagina, lista) ->
            anotacionesPorPagina[pagina] =
                lista.toMutableList()
        }

        indiceSeleccionado = -1
        paginaSeleccionada = -1
        invalidate()
    }

    fun limpiar() {
        capturarHistorial()
        anotacionesPorPagina.clear()
        paginaTrazoActual.clear()
        indiceSeleccionado = -1
        paginaSeleccionada = -1
        invalidate()
    }

    /*
     * Guarda el estado completo de las anotaciones
     * antes de cada cambio para poder deshacerlo.
     */
    private fun capturarHistorial() {
        historial.add(
            anotacionesPorPagina.mapValues {
                (_, lista) ->
                lista.toList()
            }
        )
        if (historial.size > limiteHistorial) {
            historial.removeAt(0)
        }
    }

    fun obtenerAnotaciones(
        pagina: Int
    ): List<Anotacion> {
        return anotacionesPorPagina[pagina]
            ?.toList()
            ?: emptyList()
    }

    fun reciclarPaginas() {
        for (bitmap in paginas) {
            bitmap.recycle()
        }
        paginas.clear()
        offsetsY.clear()
        alturasPaginas.clear()
    }

    private fun escalaEfectiva(): Float {
        return escalaBase * multiplicadorZoom
    }

    private fun crearForma(
        tipo: TipoForma,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Anotacion {

        val pintura =
            Paint(pinturaTrazo).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        return when (tipo) {

            TipoForma.LINEA ->
                LineaForma(
                    x1,
                    y1,
                    x2,
                    y2,
                    pintura
                )

            TipoForma.FLECHA ->
                FlechaForma(
                    x1,
                    y1,
                    x2,
                    y2,
                    pintura
                )

            TipoForma.RECTANGULO ->
                RectanguloForma(
                    x1,
                    y1,
                    x2,
                    y2,
                    pintura
                )

            TipoForma.ELIPSE ->
                ElipseForma(
                    x1,
                    y1,
                    x2,
                    y2,
                    pintura
                )

            TipoForma.CAJA_TEXTO ->
                CajaTextoForma(
                    x1,
                    y1,
                    x2,
                    y2,
                    Paint(pintura).apply {
                        strokeWidth = 4f
                        pathEffect =
                            DashPathEffect(
                                floatArrayOf(16f, 10f),
                                0f
                            )
                    }
                )
        }
    }

    private fun calcularEscala() {
        if (paginas.isEmpty()) {
            return
        }
        if (width == 0) {
            return
        }
        val mayor = paginas.map {
            it.width
        }.max() ?: 1
        escalaBase =
            width.toFloat() /
                mayor.toFloat()
        desplazamientoX = 0f
        desplazamientoY = 0f
        restringirDesplazamiento()
    }

    private fun altoDocumentoPx(): Float {
        return alturasPaginas.sum()
    }

    private fun anchoDocumentoPx(): Float {
        return paginas.map {
            it.width.toFloat()
        }.max()
            ?: 1f
    }

    private fun restringirDesplazamiento() {
        if (paginas.isEmpty()) {
            return
        }

        val anchoEfectivo =
            anchoDocumentoPx() * escalaEfectiva()

        val altoEfectivo =
            altoDocumentoPx() * escalaEfectiva()

        if (anchoEfectivo <= width) {
            desplazamientoX = (
                width - anchoEfectivo
                ) / 2f
        } else {
            desplazamientoX =
                desplazamientoX.coerceIn(
                    width - anchoEfectivo,
                    0f
                )
        }

        if (altoEfectivo <= height) {
            desplazamientoY = (
                height - altoEfectivo
                ) / 2f
        } else {
            desplazamientoY =
                desplazamientoY.coerceIn(
                    height - altoEfectivo,
                    0f
                )
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        calcularEscala()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (paginas.isEmpty()) {
            return
        }
        notificarPaginaActual()

        canvas.drawColor(
            Color.parseColor("#EEEEEE")
        )

        val escala = escalaEfectiva()

        canvas.save()
        canvas.translate(
            desplazamientoX,
            desplazamientoY
        )
        canvas.scale(escala, escala)

        for (i in paginas.indices) {
            canvas.drawBitmap(
                paginas[i],
                0f,
                offsetsY.getOrElse(i) {
                    0f
                },
                null
            )

            val anotaciones =
                anotacionesPorPagina[i]

            if (
                anotaciones != null &&
                anotaciones.isNotEmpty()
            ) {
                val offset =
                    offsetsY.getOrElse(i) {
                        0f
                    }

                canvas.save()
                canvas.translate(0f, offset)

                for (j in anotaciones.indices) {
                    val anotacion =
                        anotaciones[j]

                    anotacion.dibujar(canvas)

                    if (
                        j == indiceSeleccionado &&
                        anotacion is TextoAnotacion &&
                        paginaDeAnotacion(indiceSeleccionado) == i
                    ) {
                        dibujarSeleccion(
                            canvas,
                            anotacion
                        )
                    }
                }

                canvas.restore()
            }
        }

        paginaTrazoActual.forEach {
            (paginaTrazo, trazo) ->
            canvas.save()
            canvas.translate(
                0f,
                offsetsY.getOrElse(paginaTrazo) {
                    0f
                }
            )
            trazo.dibujar(canvas)
            canvas.restore()
        }

        formaEnProgreso?.let { forma ->
            canvas.save()
            canvas.translate(
                0f,
                offsetsY.getOrElse(formaPagina) {
                    0f
                }
            )
            forma.dibujar(canvas)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun dibujarSeleccion(
        canvas: Canvas,
        texto: TextoAnotacion
    ) {
        val rect = texto.dimensiones()
        canvas.save()
        canvas.translate(texto.x, texto.y)
        canvas.rotate(texto.rotacion)
        canvas.drawRect(rect, pinturaSeleccion)
        canvas.restore()
    }

    /*
     * Convierte un punto del documento
     * (coordenadas de página renderizada)
     * en la página y coordenadas locales.
     */
    private fun paginaEnY(
        yDoc: Float
    ): Int {
        if (paginas.isEmpty()) {
            return 0
        }
        for (i in paginas.indices) {
            val inicio =
                offsetsY.getOrElse(i) {
                    0f
                }
            val fin =
                inicio +
                    alturasPaginas.getOrElse(i) {
                        0f
                    }
            if (
                yDoc >= inicio &&
                yDoc <= fin
            ) {
                return i
            }
        }
        if (yDoc < 0f) {
            return 0
        }
        return paginas.size - 1
    }

    private fun puntoEnPagina(
        eventX: Float,
        eventY: Float
    ): Triple<Int, Float, Float> {
        val escala = escalaEfectiva()
        val xDoc =
            (eventX - desplazamientoX) / escala
        val yDoc =
            (eventY - desplazamientoY) / escala

        val pagina = paginaEnY(yDoc)
        val offset =
            offsetsY.getOrElse(pagina) {
                0f
            }

        return Triple(
            pagina,
            xDoc,
            yDoc - offset
        )
    }

    private fun paginaDeAnotacion(
        indice: Int
    ): Int? {
        for (
            (pagina, lista)
            in anotacionesPorPagina
        ) {
            if (
                indice in lista.indices
            ) {
                return pagina
            }
        }
        return null
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (paginas.isEmpty()) {
            return false
        }

        detectorZoom.onTouchEvent(event)

        val escala = escalaEfectiva()

        val (pagina, xPagina, yPagina) =
            puntoEnPagina(
                event.x,
                event.y
            )

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                ultimoX = event.x
                ultimoY = event.y

                when (modo) {

                    Modo.DIBUJO -> {
                        capturarHistorial()
                        val camino = Path()
                        camino.moveTo(
                            xPagina,
                            yPagina
                        )
                        paginaTrazoActual[pagina] =
                            Trazo(
                                camino,
                                Paint(pinturaTrazo)
                            )
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        invalidate()
                    }

                    Modo.BORRAR -> {
                        capturarHistorial()
                        val camino = Path()
                        camino.moveTo(
                            xPagina,
                            yPagina
                        )
                        paginaTrazoActual[pagina] =
                            Trazo(
                                camino,
                                Paint(pinturaBorrar)
                            )
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        invalidate()
                    }

                    Modo.RESALTAR -> {
                        capturarHistorial()
                        val camino = Path()
                        camino.moveTo(
                            xPagina,
                            yPagina
                        )
                        paginaTrazoActual[pagina] =
                            Trazo(
                                camino,
                                Paint(pinturaResaltar)
                            )
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        invalidate()
                    }

                    Modo.SUBRAYAR -> {
                        capturarHistorial()
                        inicioX = xPagina
                        inicioY = yPagina
                        val camino = Path()
                        camino.moveTo(
                            inicioX,
                            inicioY
                        )
                        camino.lineTo(
                            inicioX,
                            inicioY
                        )
                        paginaTrazoActual[pagina] =
                            Trazo(
                                camino,
                                Paint(pinturaTrazo).apply {
                                    strokeWidth = 10f
                                }
                            )
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        invalidate()
                    }

                    Modo.FORMAS -> {
                        capturarHistorial()
                        formaPagina = pagina
                        inicioX = xPagina
                        inicioY = yPagina
                        formaEnProgreso =
                            crearForma(
                                tipoFormaSeleccionada,
                                inicioX,
                                inicioY,
                                inicioX,
                                inicioY
                            )
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        invalidate()
                    }

                    Modo.DETECTAR -> {
                        val encontrado =
                            textoBajoElDedo(
                                pagina,
                                xPagina,
                                yPagina
                            )
                        alDetectarTexto?.invoke(
                            pagina,
                            encontrado
                        )
                    }

                    Modo.TEXTO -> {
                        val texto =
                            textoPendiente
                        if (
                            texto != null &&
                            texto.isNotBlank()
                        ) {
                            capturarHistorial()
                            val lista =
                                anotacionesPorPagina
                                    .getOrPut(pagina) {
                                        mutableListOf()
                                    }
                            val posicion =
                                posicionTextoLimitada(
                                    texto,
                                    xPagina,
                                    yPagina,
                                    pagina
                                )
                            lista.add(
                                TextoAnotacion(
                                    texto,
                                    posicion.first,
                                    posicion.second,
                                    Paint(
                                        pinturaTextoPendiente
                                            ?: pinturaTexto
                                    ),
                                    rotacionPendiente
                                )
                            )
                            textoPendiente = null
                            pinturaTextoPendiente = null
                            rotacionPendiente = 0f
                            indiceSeleccionado =
                                lista.size - 1
                            paginaSeleccionada = pagina
                            modo = Modo.MOVER
                            alColocarTexto?.invoke()
                            invalidate()
                        }
                    }

                    Modo.MOVER -> {
                        seleccionarEn(
                            pagina,
                            xPagina,
                            yPagina
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    paginaTrazoActual.clear()
                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (
                    detectorZoom.isInProgress
                ) {
                    ultimoX = event.x
                    ultimoY = event.y
                    return true
                }

                val deltaX =
                    event.x - ultimoX
                val deltaY =
                    event.y - ultimoY

                ultimoX = event.x
                ultimoY = event.y

                when (modo) {

                    Modo.DIBUJO,
                    Modo.BORRAR,
                    Modo.RESALTAR -> {
                        val trazo =
                            paginaTrazoActual[pagina]
                        if (trazo != null) {
                            trazo.camino.lineTo(
                                xPagina,
                                yPagina
                            )
                            invalidate()
                        }
                    }

                    Modo.SUBRAYAR -> {
                        val trazo =
                            paginaTrazoActual[pagina]
                        if (trazo != null) {
                            trazo.camino.rewind()
                            trazo.camino.moveTo(
                                inicioX,
                                inicioY
                            )
                            trazo.camino.lineTo(
                                xPagina,
                                inicioY
                            )
                            invalidate()
                        }
                    }

                    Modo.FORMAS -> {
                        if (
                            formaEnProgreso != null
                        ) {
                            formaEnProgreso =
                                crearForma(
                                    tipoFormaSeleccionada,
                                    inicioX,
                                    inicioY,
                                    xPagina,
                                    yPagina
                                )
                            invalidate()
                        }
                    }

                    Modo.MOVER -> {
                        val deltaDocX =
                            deltaX / escala
                        val deltaDocY =
                            deltaY / escala

                        val lista =
                            if (
                                paginaSeleccionada >= 0
                            ) {
                                anotacionesPorPagina[
                                    paginaSeleccionada
                                ]
                            } else {
                                null
                            }

                        val anotacion =
                            lista?.getOrNull(
                                indiceSeleccionado
                            )

                        when (anotacion) {
                            is TextoAnotacion -> {
                                lista!![indiceSeleccionado] =
                                    moverTextoConLimite(
                                        anotacion,
                                        deltaDocX,
                                        deltaDocY,
                                        paginaSeleccionada
                                    )
                                invalidate()
                            }
                            is ImagenAnotacion -> {
                                anotacion.mover(
                                    deltaDocX,
                                    deltaDocY
                                )
                                ajustarImagenEnPagina(
                                    anotacion,
                                    paginaSeleccionada
                                )
                                invalidate()
                            }
                            is FormaArrastrable -> {
                                anotacion.mover(
                                    deltaDocX,
                                    deltaDocY
                                )
                                invalidate()
                            }
                            else -> {
                                desplazar(
                                    deltaX,
                                    deltaY
                                )
                                invalidate()
                            }
                        }
                    }

                    Modo.TEXTO -> {
                        desplazar(
                            deltaX,
                            deltaY
                        )
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {

                if (
                    detectorZoom.isInProgress
                ) {
                    return true
                }

                when (modo) {

                    Modo.DIBUJO,
                    Modo.BORRAR,
                    Modo.RESALTAR,
                    Modo.SUBRAYAR -> {
                        paginaTrazoActual
                            .forEach {
                                (paginaTrazo, trazo) ->
                                anotacionesPorPagina
                                    .getOrPut(paginaTrazo) {
                                        mutableListOf()
                                    }
                                    .add(trazo)
                            }
                        paginaTrazoActual.clear()
                        parent.requestDisallowInterceptTouchEvent(
                            false
                        )
                        invalidate()
                    }

                    Modo.FORMAS -> {
                        val forma =
                            formaEnProgreso
                        if (forma != null) {
                            anotacionesPorPagina
                                .getOrPut(formaPagina) {
                                    mutableListOf()
                                }
                                .add(forma)
                            formaEnProgreso = null
                            modo = Modo.MOVER
                            alColocarForma?.invoke()
                        }
                        parent.requestDisallowInterceptTouchEvent(
                            false
                        )
                        invalidate()
                    }

                    else -> {
                        parent.requestDisallowInterceptTouchEvent(
                            false
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                paginaTrazoActual.clear()
                parent.requestDisallowInterceptTouchEvent(
                    false
                )
                return true
            }
        }

        return true
    }

    private fun desplazar(
        deltaX: Float,
        deltaY: Float
    ) {
        desplazamientoX += deltaX
        desplazamientoY += deltaY
        restringirDesplazamiento()
    }

    private fun anchoPagina(
        pagina: Int
    ): Float {
        return paginas.getOrNull(pagina)
            ?.width
            ?.toFloat()
            ?: 0f
    }

    private fun altoPagina(
        pagina: Int
    ): Float {
        return alturasPaginas.getOrElse(
            pagina
        ) {
            0f
        }
    }

    private fun posicionTextoLimitada(
        texto: String,
        xPagina: Float,
        yPagina: Float,
        pagina: Int
    ): Pair<Float, Float> {
        val prueba =
            TextoAnotacion(
                texto,
                xPagina,
                yPagina,
                Paint(pinturaTextoPendiente
                    ?: pinturaTexto),
                rotacionPendiente
            )
        return limitarTextoEnPagina(
            prueba,
            pagina
        )
    }

    private fun limitarTextoEnPagina(
        texto: TextoAnotacion,
        pagina: Int
    ): Pair<Float, Float> {
        val ancho = anchoPagina(pagina)
        val alto = altoPagina(pagina)

        if (ancho <= 0f || alto <= 0f) {
            return Pair(texto.x, texto.y)
        }

        val rect = texto.dimensiones()

        val minX = -rect.left.toFloat()
        val maxX =
            ancho - rect.right.toFloat()
        val minY = -rect.top.toFloat()
        val maxY =
            alto - rect.bottom.toFloat()

        val x =
            if (maxX >= minX) {
                texto.x.coerceIn(minX, maxX)
            } else {
                ancho / 2f
            }

        val y =
            if (maxY >= minY) {
                texto.y.coerceIn(minY, maxY)
            } else {
                alto / 2f
            }

        return Pair(x, y)
    }

    private fun moverTextoConLimite(
        texto: TextoAnotacion,
        deltaX: Float,
        deltaY: Float,
        pagina: Int
    ): TextoAnotacion {
        val (x, y) =
            limitarTextoEnPagina(
                TextoAnotacion(
                    texto.texto,
                    texto.x + deltaX,
                    texto.y + deltaY,
                    Paint(texto.pintura),
                    texto.rotacion
                ),
                pagina
            )

        return TextoAnotacion(
            texto.texto,
            x,
            y,
            Paint(texto.pintura),
            texto.rotacion
        )
    }

    private fun ajustarImagenEnPagina(
        imagen: ImagenAnotacion,
        pagina: Int
    ) {
        val ancho = anchoPagina(pagina)
        val alto = altoPagina(pagina)

        if (ancho <= 0f || alto <= 0f) {
            return
        }

        imagen.x = imagen.x.coerceIn(
            0f,
            (ancho - imagen.ancho)
                .coerceAtLeast(0f)
        )
        imagen.y = imagen.y.coerceIn(
            0f,
            (alto - imagen.alto)
                .coerceAtLeast(0f)
        )
    }

    private fun seleccionarEn(
        pagina: Int,
        xPagina: Float,
        yPagina: Float
    ) {
        val seleccionAnterior =
            indiceSeleccionado

        indiceSeleccionado = -1
        paginaSeleccionada = -1

        val lista =
            anotacionesPorPagina[pagina]
                ?: return

        for (
            i in lista.indices.reversed()
        ) {
            val anotacion =
                lista[i]

            val corresponde =
                when (anotacion) {
                    is TextoAnotacion -> {
                        val rect =
                            anotacion.dimensiones()
                        val margen = 10
                        xPagina >=
                            anotacion.x + rect.left -
                            margen &&
                            xPagina <=
                            anotacion.x + rect.right +
                            margen &&
                            yPagina >=
                            anotacion.y + rect.top -
                            margen &&
                            yPagina <=
                            anotacion.y + rect.bottom +
                            margen
                    }
                    is FormaArrastrable ->
                        anotacion.contienePunto(
                            xPagina,
                            yPagina
                        )
                    else -> false
                }

            if (corresponde) {
                indiceSeleccionado = i
                paginaSeleccionada = pagina

                if (seleccionAnterior == -1) {
                    capturarHistorial()
                }
                break
            }
        }

        invalidate()
    }
}