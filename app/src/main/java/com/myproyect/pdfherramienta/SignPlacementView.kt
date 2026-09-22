package com.myproyect.pdfherramienta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.sqrt

class SelloFirma(
    val imagen: Bitmap,
    var x: Float,
    var y: Float,
    var ancho: Float
) {
    val alto: Float
        get() = ancho * imagen.height / imagen.width
}

class SignPlacementView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pagina: Bitmap? = null
    private val sellos =
        mutableListOf<SelloFirma>()

    private var escala = 1f
    private var multiplicadorZoom = 1f
    private var desplazamientoX = 0f
    private var desplazamientoY = 0f

    private var seleccionado: SelloFirma? = null

    private var toqueX = 0f
    private var toqueY = 0f
    private var selloInicioX = 0f
    private var selloInicioY = 0f
    private var anchoInicio = 1f
    private var centroInicioX = 0f
    private var centroInicioY = 0f
    private var distanciaInicio = 1f

    private val pinturaMarco =
        Paint().apply {
            color = Color.parseColor("#E53935")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

    private val detectorZoom =
        ScaleGestureDetector(
            context,
            object :
                ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScaleBegin(
                    detector: ScaleGestureDetector
                ): Boolean {
                    if (seleccionado == null) {
                        parent.requestDisallowInterceptTouchEvent(
                            true
                        )
                        return true
                    }
                    return false
                }

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    if (seleccionado != null) {
                        return true
                    }

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
                        escala *
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
        sellosIniciales: List<SelloFirma>
    ) {
        pagina?.recycle()
        pagina = bitmap
        sellos.clear()
        sellos.addAll(sellosIniciales)
        seleccionado = null
        multiplicadorZoom = 1f
        calcularEscala()
        invalidate()
    }

    fun addSello(imagen: Bitmap) {
        val imagenPagina = pagina ?: return

        val ancho =
            imagenPagina.width * 0.4f

        val x =
            (imagenPagina.width - ancho) / 2f

        val y =
            imagenPagina.height * 0.45f

        sellos.add(
            SelloFirma(
                imagen,
                x,
                y,
                ancho
            )
        )

        invalidate()
    }

    fun obtenerSellos(): List<SelloFirma> {
        return sellos.toList()
    }

    fun borrarSeleccionado(): Boolean {
        val sello = seleccionado

        if (sello != null) {
            sellos.remove(sello)
            seleccionado = null
            invalidate()
            return true
        }

        if (sellos.isNotEmpty()) {
            sellos.removeAt(
                sellos.size - 1
            )
            invalidate()
            return true
        }

        return false
    }

    fun reciclar() {
        pagina?.recycle()
        pagina = null
    }

    private fun calcularEscala() {
        val imagen = pagina ?: return

        if (width == 0) {
            return
        }

        escala =
            width.toFloat() /
                imagen.width.toFloat()

        desplazamientoX = 0f

        desplazamientoY = (
            height - imagen.height * escala
            ) / 2f

        desplazamientoY =
            desplazamientoY.coerceAtLeast(0f)

        restringirDesplazamiento()
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

    private fun aPaginaX(x: Float): Float {
        return (x - desplazamientoX) / escalaEfectiva()
    }

    private fun aPaginaY(y: Float): Float {
        return (y - desplazamientoY) / escalaEfectiva()
    }

    private fun selloTocando(
        xPagina: Float,
        yPagina: Float
    ): SelloFirma? {

        for (
            i in sellos.indices.reversed()
        ) {

            val sello = sellos[i]

            if (
                xPagina >= sello.x &&
                xPagina <= sello.x + sello.ancho &&
                yPagina >= sello.y &&
                yPagina <= sello.y + sello.alto
            ) {
                return sello
            }
        }

        return null
    }

    private fun distancia(
        event: MotionEvent
    ): Float {
        val dx =
            event.getX(0) -
                event.getX(1)
        val dy =
            event.getY(0) -
                event.getY(1)

        return sqrt(
            dx * dx + dy * dy
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val imagenPagina = pagina ?: return

        canvas.drawColor(
            Color.parseColor("#EEEEEE")
        )

        canvas.save()

        canvas.translate(
            desplazamientoX,
            desplazamientoY
        )

        canvas.scale(
            escalaEfectiva(),
            escalaEfectiva()
        )

        canvas.drawBitmap(
            imagenPagina,
            0f,
            0f,
            null
        )

        for (sello in sellos) {

            canvas.drawBitmap(
                sello.imagen,
                null,
                RectF(
                    sello.x,
                    sello.y,
                    sello.x + sello.ancho,
                    sello.y + sello.alto
                ),
                null
            )
        }

        seleccionado?.let { sello ->
            canvas.drawRect(
                RectF(
                    sello.x,
                    sello.y,
                    sello.x + sello.ancho,
                    sello.y + sello.alto
                ),
                pinturaMarco
            )
        }

        canvas.restore()
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        val imagenPagina = pagina ?: return false

        detectorZoom.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                val xPagina =
                    aPaginaX(event.x)
                val yPagina =
                    aPaginaY(event.y)

                seleccionado =
                    selloTocando(
                        xPagina,
                        yPagina
                    )

                toqueX = event.x
                toqueY = event.y

                if (seleccionado != null) {

                    val sello = seleccionado!!

                    selloInicioX = sello.x
                    selloInicioY = sello.y
                    anchoInicio = sello.ancho

                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (
                    seleccionado != null &&
                    event.pointerCount == 2
                ) {

                    val sello = seleccionado!!

                    anchoInicio = sello.ancho
                    centroInicioX =
                        sello.x +
                            sello.ancho / 2f
                    centroInicioY =
                        sello.y +
                            sello.alto / 2f
                    distanciaInicio =
                        distancia(event)
                }

                parent.requestDisallowInterceptTouchEvent(
                    true
                )

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val sello = seleccionado

                if (!detectorZoom.isInProgress) {

                    if (sello != null) {

                        if (event.pointerCount == 1) {

                            val xPagina =
                                aPaginaX(event.x)
                            val yPagina =
                                aPaginaY(event.y)

                            sello.x =
                                selloInicioX +
                                    (event.x - toqueX) /
                                    escalaEfectiva()
                            sello.y =
                                selloInicioY +
                                    (event.y - toqueY) /
                                    escalaEfectiva()

                            selloInicioX = sello.x
                            selloInicioY = sello.y

                            ajustarDentro(
                                sello,
                                imagenPagina
                            )

                        } else if (
                            event.pointerCount == 2 &&
                            distanciaInicio > 0f
                        ) {

                            val numeroAncho =
                                anchoInicio *
                                    distancia(event) /
                                    distanciaInicio

                            val nuevoAncho =
                                numeroAncho.coerceIn(
                                    30f,
                                    imagenPagina.width.toFloat()
                                )

                            sello.x =
                                centroInicioX -
                                    nuevoAncho / 2f
                            sello.y =
                                centroInicioY -
                                    (nuevoAncho *
                                        sello.imagen.height /
                                        sello.imagen.width) / 2f
                            sello.ancho = nuevoAncho

                            ajustarDentro(
                                sello,
                                imagenPagina
                            )
                        }
                    } else {
                        desplazar(
                            event.x - toqueX,
                            event.y - toqueY
                        )
                    }
                }

                toqueX = event.x
                toqueY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (
                    detectorZoom.isInProgress
                ) {
                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                seleccionado = null
                parent.requestDisallowInterceptTouchEvent(
                    false
                )
                invalidate()
                return true
            }
        }

        return false
    }

    private fun escalaEfectiva(): Float {
        return escala * multiplicadorZoom
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

    private fun desplazar(
        deltaX: Float,
        deltaY: Float
    ) {
        desplazamientoX += deltaX
        desplazamientoY += deltaY
        restringirDesplazamiento()
    }

    private fun ajustarDentro(
        sello: SelloFirma,
        imagenPagina: Bitmap
    ) {
        val maxX =
            imagenPagina.width -
                sello.ancho
        val maxY =
            imagenPagina.height -
                sello.alto

        sello.x =
            sello.x.coerceIn(
                0f,
                maxX.coerceAtLeast(0f)
            )

        sello.y =
            sello.y.coerceIn(
                0f,
                maxY.coerceAtLeast(0f)
            )
    }
}