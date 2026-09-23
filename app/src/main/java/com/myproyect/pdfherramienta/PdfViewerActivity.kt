package com.myproyect.pdfherramienta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max


class PdfViewerActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    private lateinit var contenedorPaginas: GlobalZoomDocumentView
    private lateinit var txtTituloPdf: TextView
    private lateinit var txtEstado: TextView

    private var destruido = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pdf_viewer)

        contenedorPaginas =
            findViewById(R.id.contenedorPaginas)

        txtTituloPdf =
            findViewById(R.id.txtTituloPdf)

        txtEstado =
            findViewById(R.id.txtEstado)

        val btnEditar =
            findViewById<Button>(
                R.id.btnEditarDesdeVisor
            )

        btnEditar.setOnClickListener {

            val uri = intent.data

            if (uri != null) {

                val editarIntent =
                    Intent(
                        this,
                        PdfEditorActivity::class.java
                    )

                editarIntent.data = uri

                editarIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                startActivity(editarIntent)
            }
        }

        val uri = intent.data

        if (uri == null) {

            mostrarError(
                "No se recibió ningún PDF."
            )

            return
        }

        try {

            val archivo =
                copiarPdfTemporal(uri)

            descriptor =
                ParcelFileDescriptor.open(
                    archivo,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )

            renderer =
                PdfRenderer(descriptor!!)

            txtTituloPdf.text =
                "PDF — ${renderer!!.pageCount} páginas"

            mostrarPaginas()

        } catch (e: Exception) {

            mostrarError(
                "No se pudo abrir el PDF:\n${e.message}"
            )
        }
    }


    private fun copiarPdfTemporal(
        uri: Uri
    ): File {

        val archivo =
            File(
                cacheDir,
                "documento.pdf"
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


    private fun mostrarPaginas() {

        val pdf = renderer ?: return

        contenedorPaginas.removeAllViews()

        val totalPaginas = pdf.pageCount

        txtTituloPdf.text =
            "PDF — $totalPaginas páginas"

        txtEstado.text =
            "Cargando páginas…"

        /*
         * Ancho base adaptado a la pantalla:
         * suficiente nitidez y mucho menos
         * memoria que el fijo de 1200.
         */
        val anchoBase =
            max(
                480,
                resources.displayMetrics.widthPixels
            )

        Thread {

            for (
                numeroPagina in
                0 until totalPaginas
            ) {

                if (destruido) {
                    return@Thread
                }

                val pagina =
                    pdf.openPage(numeroPagina)

                val ancho =
                    pagina.width

                val alto =
                    pagina.height

                val escala =
                    anchoBase.toFloat() /
                        ancho.toFloat()

                val altoBase =
                    (alto * escala).toInt()

                val bitmap =
                    Bitmap.createBitmap(
                        anchoBase,
                        altoBase,
                        Bitmap.Config.ARGB_8888
                    )

                bitmap.eraseColor(
                    Color.WHITE
                )

                pagina.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                pagina.close()

                runOnUiThread {

                    if (destruido) {
                        return@runOnUiThread
                    }

                    val imagen =
                        ImageView(this)

                    imagen.setImageBitmap(bitmap)

                    imagen.scaleType =
                        ImageView.ScaleType.FIT_XY

                    imagen.setBackgroundColor(
                        Color.WHITE
                    )

                    val parametros =
                        GlobalZoomDocumentView.LayoutParams(
                            anchoBase,
                            altoBase
                        )

                    parametros.bottomMargin = 20

                    imagen.layoutParams =
                        parametros

                    imagen.isClickable = false
                    imagen.isFocusable = false

                    contenedorPaginas.addView(
                        imagen
                    )

                    txtEstado.text =
                        "Página ${
                            numeroPagina + 1
                        } de $totalPaginas"
                }
            }

            if (!destruido) {
                runOnUiThread {
                    txtEstado.text =
                        "$totalPaginas páginas"
                }
            }

        }.start()
    }


    private fun mostrarError(
        mensaje: String
    ) {

        contenedorPaginas.removeAllViews()

        val texto =
            TextView(this)

        texto.text = mensaje
        texto.textSize = 16f
        texto.gravity = Gravity.CENTER

        texto.setPadding(
            30,
            30,
            30,
            30
        )

        contenedorPaginas.addView(
            texto
        )
    }


    override fun onDestroy() {

        destruido = true

        renderer?.close()
        descriptor?.close()

        super.onDestroy()
    }
}


/*
 * =========================================================
 * VISOR GLOBAL DEL DOCUMENTO
 * =========================================================
 *
 * El zoom pertenece a TODO este contenedor.
 *
 * 1x:
 * Página 1
 * Página 2
 * Página 3
 *
 * 2x:
 * Página 1 ampliada
 * Página 2 ampliada
 * Página 3 ampliada
 *
 * 3x:
 * Todo el documento ampliado.
 *
 * El ScrollView sigue siendo responsable
 * de desplazarse entre las páginas.
 */
class GlobalZoomDocumentView(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : LinearLayout(
    context,
    attrs
) {

    private var escalaActual = 1f

    private val escalaMinima = 1f
    private val escalaMaxima = 4f

    private var objetivoScrollX: Int? = null
    private var objetivoScrollY: Int? = null

    private val detectorZoom =
        ScaleGestureDetector(
            context,
            object :
                ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScaleBegin(
                    detector: ScaleGestureDetector
                ): Boolean {

                    /*
                     * Comienza el pellizco.
                     *
                     * Bloqueamos temporalmente
                     * los ScrollView padres.
                     */
                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )

                    return true
                }

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {

                    val escalaAnterior =
                        escalaActual

                    val nuevaEscala =
                        (
                            escalaAnterior *
                                detector.scaleFactor
                        ).coerceIn(
                            escalaMinima,
                            escalaMaxima
                        )

                    if (
                        nuevaEscala ==
                        escalaAnterior
                    ) {

                        return true
                    }

                    /*
                     * Punto del documento
                     * que queda bajo los dedos.
                     */
                    val focoX =
                        detector.focusX

                    val focoY =
                        detector.focusY

                    val contenedorH =
                        scrollHorizontal()

                    val contenedorV =
                        scrollVertical()

                    val scrollX =
                        contenedorH?.scrollX
                            ?: 0

                    val scrollY =
                        contenedorV?.scrollY
                            ?: 0

                    val docX =
                        focoX /
                            escalaAnterior

                    val docY =
                        focoY /
                            escalaAnterior

                    escalaActual =
                        nuevaEscala

                    /*
                     * Nuevo scroll para que el
                     * punto del documento siga
                     * bajo los dedos.
                     */
                    objetivoScrollX =
                        (
                            docX * nuevaEscala -
                                focoX +
                                scrollX
                        ).toInt().coerceAtLeast(0)

                    objetivoScrollY =
                        (
                            docY * nuevaEscala -
                                focoY +
                                scrollY
                        ).toInt().coerceAtLeast(0)

                    requestLayout()

                    invalidate()

                    aplicarScrollObjetivo()

                    return true
                }

                override fun onScaleEnd(
                    detector: ScaleGestureDetector
                ) {

                    /*
                     * Si volvemos prácticamente
                     * a 1x, fijamos exactamente 1x.
                     */
                    if (
                        escalaActual < 1.05f
                    ) {

                        escalaActual = 1f

                        requestLayout()
                        invalidate()
                        aplicarScrollObjetivo()
                    }

                    /*
                     * El ScrollView vuelve a
                     * recuperar el control.
                     */
                    parent.requestDisallowInterceptTouchEvent(
                        false
                    )
                }
            }
        )


    /*
     * Aplica el scroll calculado después de
     * que el layout ya midió el nuevo tamaño.
     */
    private fun aplicarScrollObjetivo() {

        viewTreeObserver
            .addOnGlobalLayoutListener(
                object :
                    ViewTreeObserver.OnGlobalLayoutListener {

                    override fun onGlobalLayout() {

                        viewTreeObserver
                            .removeOnGlobalLayoutListener(
                                this
                            )

                        val nuevoX =
                            objetivoScrollX

                        val nuevoY =
                            objetivoScrollY

                        objetivoScrollX = null
                        objetivoScrollY = null

                        if (nuevoX != null) {

                            scrollHorizontal()
                                ?.scrollTo(
                                    nuevoX,
                                    0
                                )
                        }

                        if (nuevoY != null) {

                            val vertical =
                                scrollVertical()

                            vertical?.scrollTo(
                                vertical.scrollX,
                                nuevoY
                            )
                        }
                    }
                }
            )
    }


    private fun scrollHorizontal():
    HorizontalScrollView? {

        var padre: View? =
            parent as View?

        while (padre != null) {

            if (
                padre is
                HorizontalScrollView
            ) {

                return padre
            }

            padre =
                padre.parent
                    as View?
        }

        return null
    }


    private fun scrollVertical():
    ScrollView? {

        var padre: View? =
            parent as View?

        while (padre != null) {

            if (
                padre is
                ScrollView
            ) {

                return padre
            }

            padre =
                padre.parent
                    as View?
        }

        return null
    }


    override fun onInterceptTouchEvent(
        event: MotionEvent
    ): Boolean {

        /*
         * Cuando aparecen dos dedos,
         * interceptamos el gesto para
         * que el ScrollView no se lo lleve.
         */
        if (
            event.actionMasked ==
            MotionEvent.ACTION_POINTER_DOWN
        ) {

            if (
                event.pointerCount >= 2
            ) {

                parent.requestDisallowInterceptTouchEvent(
                    true
                )
            }
        }

        return super.onInterceptTouchEvent(
            event
        )
    }


    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        detectorZoom.onTouchEvent(event)

        when (
            event.actionMasked
        ) {

            MotionEvent.ACTION_DOWN -> {

                /*
                 * Un solo dedo:
                 *
                 * dejamos que el ScrollView
                 * pueda desplazarse.
                 */
                parent.requestDisallowInterceptTouchEvent(
                    false
                )

                return true
            }


            MotionEvent.ACTION_POINTER_DOWN -> {

                /*
                 * Segundo dedo:
                 *
                 * ahora sí bloqueamos
                 * el desplazamiento normal.
                 */
                if (
                    event.pointerCount >= 2
                ) {

                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )
                }

                return true
            }


            MotionEvent.ACTION_MOVE -> {

                /*
                 * Durante el pellizco mantenemos
                 * bloqueados los padres.
                 */
                if (
                    detectorZoom.isInProgress
                ) {

                    parent.requestDisallowInterceptTouchEvent(
                        true
                    )
                }

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

                parent.requestDisallowInterceptTouchEvent(
                    false
                )

                return true
            }
        }

        return true
    }


    /*
     * Aquí está la parte importante.
     *
     * Aumentamos el tamaño REAL del contenedor
     * cuando hacemos zoom.
     *
     * Así el ScrollView sabe que el documento
     * realmente ocupa más espacio.
     */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {

        var anchoBase = 0
        var altoBase = 0

        /*
         * Primero medimos las páginas
         * en su tamaño normal.
         */
        for (
            i in 0 until childCount
        ) {

            val hijo =
                getChildAt(i)

            measureChildWithMargins(
                hijo,
                widthMeasureSpec,
                0,
                heightMeasureSpec,
                0
            )

            val parametros =
                hijo.layoutParams
                    as MarginLayoutParams

            anchoBase =
                max(
                    anchoBase,
                    hijo.measuredWidth +
                        parametros.leftMargin +
                        parametros.rightMargin
                )

            altoBase +=
                hijo.measuredHeight +
                    parametros.topMargin +
                    parametros.bottomMargin
        }

        /*
         * Añadimos el padding.
         */
        anchoBase +=
            paddingLeft +
            paddingRight

        altoBase +=
            paddingTop +
            paddingBottom

        /*
         * Ahora aplicamos el zoom
         * a TODO el documento.
         */
        val anchoFinal =
            ceil(
                anchoBase *
                    escalaActual
            ).toInt()

        val altoFinal =
            ceil(
                altoBase *
                    escalaActual
            ).toInt()

        setMeasuredDimension(
            resolveSize(
                anchoFinal,
                widthMeasureSpec
            ),
            resolveSize(
                altoFinal,
                heightMeasureSpec
            )
        )
    }


    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {

        /*
         * Las páginas mantienen sus posiciones
         * normales.
         *
         * El Canvas completo se escala al dibujar.
         */
        var posicionY =
            paddingTop

        for (
            i in 0 until childCount
        ) {

            val hijo =
                getChildAt(i)

            val parametros =
                hijo.layoutParams
                    as MarginLayoutParams

            val izquierda =
                paddingLeft +
                    parametros.leftMargin

            val arriba =
                posicionY +
                    parametros.topMargin

            val derecha =
                izquierda +
                    hijo.measuredWidth

            val abajo =
                arriba +
                    hijo.measuredHeight

            hijo.layout(
                izquierda,
                arriba,
                derecha,
                abajo
            )

            posicionY =
                abajo +
                    parametros.bottomMargin
        }
    }


    /*
     * Dibujamos TODO el documento ampliado.
     */
    override fun dispatchDraw(
        canvas: Canvas
    ) {

        canvas.save()

        canvas.scale(
            escalaActual,
            escalaActual
        )

        super.dispatchDraw(canvas)

        canvas.restore()
    }


    override fun generateDefaultLayoutParams():
    LayoutParams {

    return LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    }


    override fun generateLayoutParams(
        attrs: android.util.AttributeSet
    ): LayoutParams {

        return LayoutParams(
            context,
            attrs
        )
    }


    override fun generateLayoutParams(
        params: ViewGroup.LayoutParams
    ): LayoutParams {

        return LayoutParams(params)
    }


    override fun checkLayoutParams(
        params: ViewGroup.LayoutParams
    ): Boolean {

        return params is LayoutParams
    }


    class LayoutParams :
        LinearLayout.LayoutParams {

        constructor(
            width: Int,
            height: Int
        ) : super(width, height)

        constructor(
            params: ViewGroup.LayoutParams
        ) : super(params)

        constructor(
            context: android.content.Context,
            attrs: android.util.AttributeSet
        ) : super(
            context,
            attrs
        )
    }
}