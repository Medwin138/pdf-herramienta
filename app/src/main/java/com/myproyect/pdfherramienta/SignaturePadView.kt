package com.myproyect.pdfherramienta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignaturePadView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Trazo(
        val camino: Path,
        val pintura: Paint
    )

    private val trazos =
        mutableListOf<Trazo>()
    private var trazoActual: Trazo? = null

    private val pintura =
        Paint().apply {
            color = Color.BLACK
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

    init {
        setBackgroundColor(Color.WHITE)
    }

    fun limpiar() {
        trazos.clear()
        trazoActual = null
        invalidate()
    }

    fun tieneFirma(): Boolean {
        return trazos.isNotEmpty() ||
            trazoActual != null
    }

    fun generarFirma(): Bitmap {
        val escala = 2f

        val anchoBitMap = (
            width * escala
            ).toInt().coerceAtLeast(1)

        val altoBitMap = (
            height * escala
            ).toInt().coerceAtLeast(1)

        val firma =
            Bitmap.createBitmap(
                anchoBitMap,
                altoBitMap,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(firma)

        canvas.scale(escala, escala)

        for (trazo in trazos) {
            canvas.drawPath(
                trazo.camino,
                trazo.pintura
            )
        }

        return firma
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (trazo in trazos) {
            canvas.drawPath(
                trazo.camino,
                trazo.pintura
            )
        }

        trazoActual?.let {
            canvas.drawPath(
                it.camino,
                it.pintura
            )
        }
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                val camino = Path()
                camino.moveTo(
                    event.x,
                    event.y
                )
                trazoActual =
                    Trazo(
                        camino,
                        Paint(pintura)
                    )
                parent.requestDisallowInterceptTouchEvent(
                    true
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                trazoActual?.camino
                    ?.lineTo(
                        event.x,
                        event.y
                    )
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                trazoActual?.let {
                    trazos.add(it)
                }
                trazoActual = null
                parent.requestDisallowInterceptTouchEvent(
                    false
                )
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
}