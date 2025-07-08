package com.example.notebook_personalized.ui.drawing

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import com.example.notebook_personalized.data.model.Stroke
import com.example.notebook_personalized.data.model.PointF
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.notebook_personalized.data.model.TextElement
import com.example.notebook_personalized.data.model.ImageElement
import android.graphics.Rect
import android.graphics.Paint.Align
import com.example.notebook_personalized.data.model.DrawingData
import com.example.notebook_personalized.data.model.ImageElementSerializable
import java.io.File
import java.io.FileOutputStream
import android.widget.EditText
import android.widget.FrameLayout
import android.view.inputmethod.InputMethodManager
import com.example.notebook_personalized.R
import android.view.ViewGroup

class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var drawColor: Int = Color.BLACK
    private var strokeWidth: Float = 10f
    private val drawPaint = Paint().apply {
        color = drawColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        this.strokeWidth = this@DrawingCanvasView.strokeWidth
    }

    private var currentPath: Path = Path()
    private val paths = mutableListOf<Pair<Path, Paint>>()
    private val undonePaths = mutableListOf<Pair<Path, Paint>>()

    private var isEraserOn: Boolean = false
    private val backgroundColor: Int = Color.WHITE // Puedes cambiarlo si tu fondo es de otro color

    private val strokes = mutableListOf<Stroke>()
    private val textElements = mutableListOf<TextElement>()
    private val imageElements = mutableListOf<ImageElement>()
    private var selectedText: TextElement? = null
    private var movingText: TextElement? = null
    private var movingImage: ImageElement? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 48f
        isAntiAlias = true
        textAlign = Align.LEFT
    }

    private var lastPathX = 0f
    private var lastPathY = 0f

    private var currentStroke: Stroke? = null

    private var activeEditText: EditText? = null
    private var activeTextElement: TextElement? = null

    enum class Tool { NONE, DRAW, ERASER, TEXT, IMAGE }
    private var activeTool: Tool = Tool.DRAW

    private var selectedImage: ImageElement? = null
    private var resizingHandle: Int = -1 // 0: top-left, 1: top-right, 2: bottom-right, 3: bottom-left
    private val handleSize = 40f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor)
        // Dibuja paths
        for ((path, paint) in paths) {
            canvas.drawPath(path, paint)
        }
        canvas.drawPath(currentPath, drawPaint)
        // Dibuja imágenes
        for (img in imageElements) {
            canvas.drawBitmap(img.bitmap, null, RectF(img.x, img.y, img.x+img.width, img.y+img.height), null)
            // Si está seleccionada, dibujar borde y handles
            if (img == selectedImage) {
                val borderPaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRect(img.x, img.y, img.x+img.width, img.y+img.height, borderPaint)
                val handlePaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                // Esquinas: 0=TL, 1=TR, 2=BR, 3=BL
                val handles = arrayOf(
                    Pair(img.x, img.y),
                    Pair(img.x+img.width, img.y),
                    Pair(img.x+img.width, img.y+img.height),
                    Pair(img.x, img.y+img.height)
                )
                for ((hx, hy) in handles) {
                    canvas.drawCircle(hx, hy, handleSize, handlePaint)
                    canvas.drawCircle(hx, hy, handleSize-4, borderPaint)
                }
            }
        }
        // Dibuja textos (oculta el que está siendo editado)
        for (txt in textElements) {
            if (activeTextElement != null && txt === activeTextElement && activeEditText != null) continue
            textPaint.color = txt.color
            textPaint.textSize = txt.textSize
            textPaint.style = Paint.Style.FILL
            // Calcular altura del texto
            val bounds = Rect()
            textPaint.getTextBounds(txt.text, 0, txt.text.length, bounds)
            val textHeight = bounds.height()
            // Dibujar el texto alineando la baseline correctamente
            canvas.drawText(txt.text, txt.x, txt.y + textHeight, textPaint)
            if (txt.isSelected) {
                bounds.offset(txt.x.toInt(), txt.y.toInt())
                val selPaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawRect(bounds, selPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (activeTool == Tool.IMAGE) {
            val x = event.x
            val y = event.y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // ¿Tocó un handle de la imagen seleccionada?
                    if (selectedImage != null) {
                        val handles = arrayOf(
                            Pair(selectedImage!!.x, selectedImage!!.y),
                            Pair(selectedImage!!.x+selectedImage!!.width, selectedImage!!.y),
                            Pair(selectedImage!!.x+selectedImage!!.width, selectedImage!!.y+selectedImage!!.height),
                            Pair(selectedImage!!.x, selectedImage!!.y+selectedImage!!.height)
                        )
                        for ((i, handle) in handles.withIndex()) {
                            if (Math.abs(x-handle.first) < handleSize && Math.abs(y-handle.second) < handleSize) {
                                resizingHandle = i
                                return true
                            }
                        }
                    }
                    // ¿Tocó una imagen?
                    val img = findImageAt(x, y)
                    if (img != null) {
                        selectedImage = img
                        movingImage = img
                        lastTouchX = x
                        lastTouchY = y
                        resizingHandle = -1
                        invalidate()
                        return true
                    }
                    // Tocar fuera de cualquier imagen: permitir agregar nueva
                    if (selectedImage != null) {
                        selectedImage = null
                        resizingHandle = -1
                        invalidate()
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (resizingHandle != -1 && selectedImage != null) {
                        // Redimensionar según el handle
                        val img = selectedImage!!
                        val minSize = 60f
                        when (resizingHandle) {
                            0 -> { // top-left
                                val newRight = img.x + img.width
                                val newBottom = img.y + img.height
                                img.x = x.coerceAtMost(newRight-minSize)
                                img.y = y.coerceAtMost(newBottom-minSize)
                                img.width = newRight - img.x
                                img.height = newBottom - img.y
                            }
                            1 -> { // top-right
                                val newLeft = img.x
                                val newBottom = img.y + img.height
                                img.y = y.coerceAtMost(newBottom-minSize)
                                img.width = (x - newLeft).coerceAtLeast(minSize)
                                img.height = newBottom - img.y
                            }
                            2 -> { // bottom-right
                                val newLeft = img.x
                                val newTop = img.y
                                img.width = (x - newLeft).coerceAtLeast(minSize)
                                img.height = (y - newTop).coerceAtLeast(minSize)
                            }
                            3 -> { // bottom-left
                                val newRight = img.x + img.width
                                val newTop = img.y
                                img.x = x.coerceAtMost(newRight-minSize)
                                img.width = newRight - img.x
                                img.height = (y - newTop).coerceAtLeast(minSize)
                            }
                        }
                        rescaleSelectedImage()
                        invalidate()
                        return true
                    }
                    if (movingImage != null) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        movingImage?.x = (movingImage?.x ?: 0f) + dx
                        movingImage?.y = (movingImage?.y ?: 0f) + dy
                        lastTouchX = x
                        lastTouchY = y
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    movingImage = null
                    resizingHandle = -1
                }
            }
            return true
        }
        // Solo permitir dibujar si la herramienta activa es DRAW o ERASER
        if (activeTool != Tool.DRAW && activeTool != Tool.ERASER) return false
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Prioridad: seleccionar texto o imagen
                selectedText = findTextAt(x, y)
                movingText = selectedText
                movingImage = findImageAt(x, y)
                lastTouchX = x
                lastTouchY = y
                if (movingText != null) {
                    textElements.forEach { it.isSelected = false }
                    movingText?.isSelected = true
                    invalidate()
                    return true
                } else if (movingImage != null) {
                    invalidate()
                    return true
                } else {
                    textElements.forEach { it.isSelected = false }
                }
                currentPath = Path()
                currentPath.moveTo(x, y)
                lastPathX = x
                lastPathY = y
                // Crear nuevo Stroke y agregar primer punto
                currentStroke = Stroke(
                    color = drawPaint.color,
                    strokeWidth = drawPaint.strokeWidth,
                    points = mutableListOf(PointF(x, y))
                )
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (movingText != null) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    movingText?.x = (movingText?.x ?: 0f) + dx
                    movingText?.y = (movingText?.y ?: 0f) + dy
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                } else if (movingImage != null) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    movingImage?.x = (movingImage?.x ?: 0f) + dx
                    movingImage?.y = (movingImage?.y ?: 0f) + dy
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
                // Trazado suave con quadTo
                val dx = Math.abs(x - lastPathX)
                val dy = Math.abs(y - lastPathY)
                if (dx >= 2f || dy >= 2f) {
                    currentPath.quadTo(lastPathX, lastPathY, (x + lastPathX) / 2, (y + lastPathY) / 2)
                    lastPathX = x
                    lastPathY = y
                    // Agregar punto al Stroke
                    currentStroke?.points?.add(PointF(x, y))
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                movingText = null
                movingImage = null
                currentPath.lineTo(x, y)
                val newPaint = Paint(drawPaint)
                paths.add(Pair(currentPath, newPaint))
                // Finalizar y guardar el Stroke
                currentStroke?.points?.add(PointF(x, y))
                currentStroke?.let { strokes.add(it) }
                currentStroke = null
                currentPath = Path()
                undonePaths.clear()
                invalidate()
            }
        }
        return true
    }

    private fun findTextAt(x: Float, y: Float): TextElement? {
        for (txt in textElements.reversed()) {
            textPaint.textSize = txt.textSize
            val bounds = Rect()
            textPaint.getTextBounds(txt.text, 0, txt.text.length, bounds)
            val left = txt.x
            val top = txt.y
            val right = txt.x + bounds.width()
            val bottom = txt.y + bounds.height()
            if (x in left..right && y in top..bottom) {
                return txt
            }
        }
        return null
    }

    fun findImageAt(x: Float, y: Float): ImageElement? {
        val tolerance = 2f // margen para toques imprecisos
        for (img in imageElements.reversed()) {
            if (x >= img.x - tolerance && x <= img.x + img.width + tolerance &&
                y >= img.y - tolerance && y <= img.y + img.height + tolerance) {
                return img
            }
        }
        return null
    }

    // Métodos públicos para futuras herramientas
    fun setColor(color: Int) {
        drawColor = color
        drawPaint.color = color
    }

    fun setStrokeWidth(width: Float) {
        strokeWidth = width
        drawPaint.strokeWidth = width
    }

    fun clearCanvas() {
        paths.clear()
        currentPath.reset()
        undonePaths.clear()
        invalidate()
    }

    fun setEraserMode(enabled: Boolean) {
        isEraserOn = enabled
        if (isEraserOn) {
            drawPaint.color = backgroundColor
        } else {
            drawPaint.color = drawColor
        }
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            val last = paths.removeAt(paths.size - 1)
            undonePaths.add(last)
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            val last = undonePaths.removeAt(undonePaths.size - 1)
            paths.add(last)
            invalidate()
        }
    }

    fun saveToGallery(context: Context): String? {
        // Crear un bitmap del tamaño del view
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)

        // Directorio público para imágenes
        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val appDir = java.io.File(picturesDir, "NotebookPersonalized")
        if (!appDir.exists()) appDir.mkdirs()

        // Nombre de archivo único
        val fileName = "note_${System.currentTimeMillis()}.png"
        val file = java.io.File(appDir, fileName)
        return try {
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            // Notificar a la galería
            val uri = android.net.Uri.fromFile(file)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveToPdf(context: Context): String? {
        return try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            draw(canvas)
            pdfDocument.finishPage(page)

            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val appDir = java.io.File(picturesDir, "NotebookPersonalized")
            if (!appDir.exists()) appDir.mkdirs()

            val fileName = "note_${System.currentTimeMillis()}.pdf"
            val file = java.io.File(appDir, fileName)
            val fos = java.io.FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.flush()
            fos.close()
            pdfDocument.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToJson(context: Context): String? {
        return try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, Stroke::class.java)
            val adapter = moshi.adapter<List<Stroke>>(type)
            val json = adapter.toJson(strokes)
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val appDir = java.io.File(picturesDir, "NotebookPersonalized")
            if (!appDir.exists()) appDir.mkdirs()
            val fileName = "note_${System.currentTimeMillis()}.json"
            val file = java.io.File(appDir, fileName)
            file.writeText(json)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Importar desde JSON
    fun importFromJson(context: Context, filePath: String): Boolean {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, Stroke::class.java)
        val adapter = moshi.adapter<List<Stroke>>(type)
        return try {
            val file = java.io.File(filePath)
            val json = file.readText()
            val loadedStrokes = adapter.fromJson(json)
            if (loadedStrokes != null) {
                strokes.clear()
                strokes.addAll(loadedStrokes)
                invalidate()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Métodos públicos para agregar y editar texto/imágenes
    fun addTextElement(text: String, x: Float, y: Float, color: Int = Color.BLACK, textSize: Float = 48f) {
        textElements.add(TextElement(text, x, y, color, textSize))
        invalidate()
    }

    fun editSelectedText(newText: String) {
        selectedText?.let {
            it.text = newText
            invalidate()
        }
    }

    fun setSelectedTextColor(color: Int) {
        selectedText?.let {
            it.color = color
            invalidate()
        }
    }

    fun setSelectedTextSize(size: Float) {
        selectedText?.let {
            it.textSize = size
            invalidate()
        }
    }

    fun addImageElement(bitmap: Bitmap, x: Float, y: Float, width: Float, height: Float, originalPath: String? = null) {
        // Mantener tamaño y calidad original, pero ajustar si excede el canvas
        val maxW = width.coerceAtMost(this.width.toFloat())
        val maxH = height.coerceAtMost(this.height.toFloat())
        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height, 1f)
        val newWidth = bitmap.width * scale
        val newHeight = bitmap.height * scale
        val scaledBitmap = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, newWidth.toInt(), newHeight.toInt(), true) else bitmap
        imageElements.add(ImageElement(scaledBitmap, x, y, newWidth, newHeight, originalPath))
        invalidate()
    }

    fun setStrokes(newStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        paths.clear()
        for (stroke in newStrokes) {
            val path = Path()
            if (stroke.points.isNotEmpty()) {
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (point in stroke.points.drop(1)) {
                    path.lineTo(point.x, point.y)
                }
            }
            val paint = Paint(drawPaint)
            paint.color = stroke.color
            paint.strokeWidth = stroke.strokeWidth
            paths.add(Pair(path, paint))
        }
        invalidate()
    }

    fun getStrokes(): List<Stroke> {
        return strokes.toList()
    }

    fun getDrawingData(context: Context): DrawingData {
        // Guardar imágenes como archivos y obtener rutas
        val imageSerializables = imageElements.mapIndexed { idx, img ->
            val fileName = "img_${System.currentTimeMillis()}_$idx.png"
            val file = File(context.filesDir, fileName)
            val fos = FileOutputStream(file)
            img.bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            ImageElementSerializable(
                imagePath = file.absolutePath,
                x = img.x,
                y = img.y,
                width = img.width,
                height = img.height
            )
        }
        return DrawingData(
            strokes = strokes.toList(),
            texts = textElements.toList(),
            images = imageSerializables
        )
    }

    fun setDrawingData(context: Context, data: DrawingData) {
        setStrokes(data.strokes)
        textElements.clear()
        textElements.addAll(data.texts)
        imageElements.clear()
        data.images.forEach { imgSer ->
            val bitmap = BitmapFactory.decodeFile(imgSer.imagePath)
            if (bitmap != null) {
                imageElements.add(ImageElement(bitmap, imgSer.x, imgSer.y, imgSer.width, imgSer.height))
            }
        }
        invalidate()
    }

    fun startTextInput(x: Float, y: Float, color: Int, textSize: Float, parent: FrameLayout) {
        // Buscar si hay un texto en la posición tocada
        val touchedText = findTextAt(x, y)
        // Eliminar EditText anterior de forma segura
        closeActiveEditText()
        if (touchedText != null) {
            // Editar texto existente
            activeTextElement = touchedText
        } else {
            // Crear nuevo texto (guardar y como esquina superior)
            val newText = TextElement("", x, y, color, textSize)
            textElements.add(newText)
            activeTextElement = newText
        }
        invalidate()
        val editText = EditText(context)
        editText.setText(activeTextElement?.text ?: "")
        editText.setTextColor(color)
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)
        editText.setBackgroundResource(R.drawable.text_input_bg)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin = (activeTextElement?.x ?: x).toInt()
        params.topMargin = (activeTextElement?.y ?: y).toInt()
        editText.layoutParams = params
        parent.addView(editText)
        editText.requestFocus()
        editText.setSelection(editText.text.length)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        activeEditText = editText
        // Actualizar el texto en tiempo real
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeTextElement?.text = s.toString()
                invalidate()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        // Permitir mover el texto arrastrando el EditText
        editText.setOnTouchListener(object : OnTouchListener {
            var lastX = 0f
            var lastY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        params.leftMargin += dx.toInt()
                        params.topMargin += dy.toInt()
                        editText.layoutParams = params
                        // Guardar la esquina superior como posición Y
                        activeTextElement?.x = params.leftMargin.toFloat()
                        activeTextElement?.y = params.topMargin.toFloat()
                        lastX = event.rawX
                        lastY = event.rawY
                        invalidate()
                    }
                }
                return false
            }
        })
        // Al perder foco, quitar el EditText y mostrar el texto fijo
        editText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val parentView = editText.parent as? ViewGroup
                if (parentView != null) {
                    try {
                        parentView.removeView(editText)
                    } catch (e: Exception) {
                        // Ya fue removido, ignorar
                    }
                }
                if (activeEditText == editText) {
                    activeEditText = null
                    activeTextElement = null
                }
                invalidate()
            }
        }
    }

    fun setActiveTool(tool: Tool) {
        activeTool = tool
    }

    fun closeActiveEditText() {
        activeEditText?.let { editText ->
            val parentView = editText.parent
            if (parentView is ViewGroup) {
                try {
                    parentView.removeView(editText)
                } catch (e: Exception) {
                    // Ya fue removido, ignorar
                }
            }
            activeEditText = null
            activeTextElement = null
            invalidate()
        }
    }

    // Al redimensionar, reescalar desde el archivo original si existe
    private fun rescaleSelectedImage() {
        val img = selectedImage ?: return
        val path = img.originalPath
        if (path != null) {
            val originalBitmap = BitmapFactory.decodeFile(path)
            if (originalBitmap != null) {
                val scaled = Bitmap.createScaledBitmap(originalBitmap, img.width.toInt(), img.height.toInt(), true)
                img.bitmap = scaled
                invalidate()
            }
        }
    }
} 