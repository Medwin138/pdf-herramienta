package com.myproyect.pdfherramienta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

interface Anotacion {
    fun dibujar(canvas: Canvas)
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
        DETECTAR
    }

    var alColocarTexto: (() -> Unit)? = null

    var alDetectarTexto: ((TextoReconocido?) -> Unit)? =
        null

    private var pagina: Bitmap? = null
    private var modo = Modo.MOVER
    private var textoPendiente: String? = null
    private var pinturaTextoPendiente: Paint? = null
    private var rotacionPendiente = 0f

    private val anotaciones = mutableListOf<Anotacion>()
    private var trazoActual: Trazo? = null
    private var indiceSeleccionado = -1

    private val textosReconocidos =
        mutableListOf<TextoReconocido>()

    private val pinturaBorrar = Paint().apply {
        color = Color.WHITE
        strokeWidth = 30f
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

    private val pinturaTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
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

    private val detectorZoom =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

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

    fun setPagina(
        bitmap: Bitmap,
        anotacionesIniciales: List<Anotacion>
    ) {
        pagina?.recycle()
        pagina = bitmap
        anotaciones.clear()
        anotaciones.addAll(anotacionesIniciales)
        trazoActual = null
        textoPendiente = null
        indiceSeleccionado = -1
        multiplicadorZoom = 1f
        calcularEscala()
        invalidate()
    }

    fun setModo(modo: Modo) {
        this.modo = modo
        trazoActual = null
        if (
            modo == Modo.DIBUJO ||
            modo == Modo.TEXTO
        ) {
            indiceSeleccionado = -1
        }
    }

    fun setTextosReconocidos(
        textos: List<TextoReconocido>
    ) {
        textosReconocidos.clear()
        textosReconocidos.addAll(textos)
    }

    fun textoBajoElDedo(
        xPagina: Float,
        yPagina: Float
    ): TextoReconocido? {

        var mejor: TextoReconocido? = null
        var mejorDistancia = Float.MAX_VALUE

        for (texto in textosReconocidos) {

            val dentro =
                xPagina >= texto.izquierda &&
                    xPagina <= texto.derecha &&
                    yPagina >= texto.superior &&
                    yPagina <= texto.inferior

            if (dentro) {

                val centroX =
                    (texto.izquierda + texto.derecha) / 2f
                val centroY =
                    (texto.superior + texto.inferior) / 2f

                val distancia =
                    (xPagina - centroX) *
                        (xPagina - centroX) +
                        (yPagina - centroY) *
                        (yPagina - centroY)

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

    fun setTexto(texto: String) {
        textoPendiente = texto
        pinturaTextoPendiente = null
        rotacionPendiente = 0f
    }

    fun setTexto(texto: String, pintura: Paint) {
        setTexto(texto, pintura, 0f)
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
        val anotacion =
            anotaciones.getOrNull(
                indiceSeleccionado
            )

        return anotacion as? TextoAnotacion
    }

    fun editarTextoSeleccionado(
        texto: String,
        pintura: Paint
    ) {
        val anotacion =
            anotaciones.getOrNull(
                indiceSeleccionado
            )

        if (anotacion is TextoAnotacion) {
            anotaciones[indiceSeleccionado] =
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

    fun girarSeleccionado(
        grados: Float
    ) {
        val anotacion =
            anotaciones.getOrNull(
                indiceSeleccionado
            )

        if (anotacion is TextoAnotacion) {
            anotaciones[indiceSeleccionado] =
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

    fun girarSeleccionadoA(grados: Float) {
        val anotacion =
            anotaciones.getOrNull(
                indiceSeleccionado
            )

        if (anotacion is TextoAnotacion) {
            anotaciones[indiceSeleccionado] =
                TextoAnotacion(
                    anotacion.texto,
                    anotacion.x,
                    anotacion.y,
                    Paint(anotacion.pintura),
                    grados
                )
            invalidate()
        }
    }

    fun eliminarSeleccionado() {
        if (
            indiceSeleccionado >= 0 &&
            indiceSeleccionado < anotaciones.size
        ) {
            anotaciones.removeAt(indiceSeleccionado)
            indiceSeleccionado = -1
            invalidate()
        }
    }

    fun deshacer() {
        if (anotaciones.isNotEmpty()) {
            anotaciones.removeAt(anotaciones.size - 1)
            indiceSeleccionado = -1
            invalidate()
        }
    }

    fun limpiar() {
        anotaciones.clear()
        trazoActual = null
        indiceSeleccionado = -1
        invalidate()
    }

    fun obtenerAnotaciones(): List<Anotacion> {
        return anotaciones.toList()
    }

    fun reciclarPagina() {
        pagina?.recycle()
        pagina = null
    }

    private fun escalaEfectiva(): Float {
        return escalaBase * multiplicadorZoom
    }

    private fun calcularEscala() {
        val imagen = pagina ?: return
        if (width == 0) {
            return
        }
        escalaBase =
            width.toFloat() /
                imagen.width.toFloat()
        desplazamientoX = 0f
        desplazamientoY = (
            height - imagen.height * escalaBase
            ) / 2f
        desplazamientoY =
            desplazamientoY.coerceAtLeast(0f)
        restringirDesplazamiento()
    }

    private fun restringirDesplazamiento() {
        val imagen = pagina ?: return

        val anchoEfectivo =
            imagen.width * escalaEfectiva()

        val altoEfectivo =
            imagen.height * escalaEfectiva()

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
        val imagen = pagina ?: return
        canvas.drawColor(Color.parseColor("#EEEEEE"))

        val escala = escalaEfectiva()

        canvas.save()
        canvas.translate(
            desplazamientoX,
            desplazamientoY
        )
        canvas.scale(escala, escala)
        canvas.drawBitmap(imagen, 0f, 0f, null)

        for (
            i in anotaciones.indices
        ) {
            val anotacion =
                anotaciones[i]

            anotacion.dibujar(canvas)

            if (
                i == indiceSeleccionado &&
                anotacion is TextoAnotacion
            ) {
                dibujarSeleccion(canvas, anotacion)
            }
        }

        trazoActual?.dibujar(canvas)
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

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        val imagen = pagina ?: return false

        detectorZoom.onTouchEvent(event)

        val escala = escalaEfectiva()

        val xPagina = (
            event.x - desplazamientoX
            ) / escala
        val yPagina = (
            event.y - desplazamientoY
            ) / escala

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                ultimoX = event.x
                ultimoY = event.y

                when (modo) {

                    Modo.DIBUJO -> {
                        val camino = Path()
                        camino.moveTo(
                            xPagina,
                            yPagina
                        )
                        trazoActual =
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
                        val camino = Path()
                        camino.moveTo(
                            xPagina,
                            yPagina
                        )
                        trazoActual =
                            Trazo(
                                camino,
                                Paint(pinturaBorrar)
                            )
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        invalidate()
                    }

                    Modo.DETECTAR -> {
                        val encontrado =
                            textoBajoElDedo(
                                xPagina,
                                yPagina
                            )
                        alDetectarTexto?.invoke(
                            encontrado
                        )
                    }

                    Modo.TEXTO -> {
                        val texto = textoPendiente
                        if (
                            texto != null &&
                            texto.isNotBlank()
                        ) {
                            anotaciones.add(
                                TextoAnotacion(
                                    texto,
                                    xPagina,
                                    yPagina,
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
                                anotaciones.size - 1
                            modo = Modo.MOVER
                            alColocarTexto?.invoke()
                            invalidate()
                        }
                    }

                    Modo.MOVER -> {
                        seleccionarEn(
                            xPagina,
                            yPagina
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    trazoActual = null
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

                val deltaX = event.x - ultimoX
                val deltaY = event.y - ultimoY
                ultimoX = event.x
                ultimoY = event.y

                when (modo) {

                    Modo.DIBUJO -> {
                        if (trazoActual != null) {
                            trazoActual?.camino?.lineTo(
                                xPagina,
                                yPagina
                            )
                            invalidate()
                        }
                    }

                    Modo.BORRAR -> {
                        if (trazoActual != null) {
                            trazoActual?.camino?.lineTo(
                                xPagina,
                                yPagina
                            )
                            invalidate()
                        }
                    }

                    Modo.MOVER -> {
                        val anotacion =
                            anotaciones.getOrNull(
                                indiceSeleccionado
                            )

                        if (
                            anotacion is
                            TextoAnotacion
                        ) {
                            anotaciones[indiceSeleccionado] =
                                TextoAnotacion(
                                    anotacion.texto,
                                    anotacion.x +
                                        deltaX / escala,
                                    anotacion.y +
                                        deltaY / escala,
                                    Paint(anotacion.pintura),
                                    anotacion.rotacion
                                )
                            invalidate()
                        } else {
                            desplazar(
                                deltaX,
                                deltaY
                            )
                            invalidate()
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
                    Modo.BORRAR -> {
                        trazoActual?.let {
                            anotaciones.add(it)
                        }
                        trazoActual = null
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
                trazoActual = null
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

    private fun seleccionarEn(
        xPagina: Float,
        yPagina: Float
    ) {
        indiceSeleccionado = -1

        for (
            i in anotaciones.indices.reversed()
        ) {
            val anotacion =
                anotaciones[i]

            if (
                anotacion is TextoAnotacion
            ) {
                val rect =
                    anotacion.dimensiones()

                if (
                    xPagina >=
                        anotacion.x + rect.left &&
                    xPagina <=
                        anotacion.x + rect.right &&
                    yPagina >=
                        anotacion.y + rect.top &&
                    yPagina <=
                        anotacion.y + rect.bottom
                ) {
                    indiceSeleccionado = i
                    break
                }
            }
        }

        invalidate()
    }
}