package com.example.notebook_personalized.ui.drawing

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.notebook_personalized.data.model.Stroke
import com.example.notebook_personalized.data.model.PointF
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.notebook_personalized.data.model.TextElement
import com.example.notebook_personalized.data.model.ImageElement
import android.graphics.Rect
import android.graphics.Paint.Align

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
            canvas.drawBitmap(img.bitmap, null, Rect(img.x.toInt(), img.y.toInt(), (img.x+img.width).toInt(), (img.y+img.height).toInt()), null)
        }
        // Dibuja textos
        for (txt in textElements) {
            textPaint.color = txt.color
            textPaint.textSize = txt.textSize
            textPaint.style = Paint.Style.FILL
            canvas.drawText(txt.text, txt.x, txt.y, textPaint)
            if (txt.isSelected) {
                // Dibuja un rectángulo alrededor del texto seleccionado
                val bounds = Rect()
                textPaint.getTextBounds(txt.text, 0, txt.text.length, bounds)
                bounds.offset(txt.x.toInt(), txt.y.toInt() - bounds.height())
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
                currentPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                movingText = null
                movingImage = null
                val newPaint = Paint(drawPaint)
                paths.add(Pair(currentPath, newPaint))
                currentPath = Path()
                undonePaths.clear()
                invalidate()
            }
        }
        return true
    }

    private fun findTextAt(x: Float, y: Float): TextElement? {
        for (txt in textElements.reversed()) {
            val bounds = Rect()
            textPaint.textSize = txt.textSize
            textPaint.getTextBounds(txt.text, 0, txt.text.length, bounds)
            val left = txt.x
            val top = txt.y - bounds.height()
            val right = txt.x + bounds.width()
            val bottom = txt.y
            if (x in left..right && y in top..bottom) {
                return txt
            }
        }
        return null
    }

    private fun findImageAt(x: Float, y: Float): ImageElement? {
        for (img in imageElements.reversed()) {
            if (x >= img.x && x <= img.x + img.width && y >= img.y && y <= img.y + img.height) {
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
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        draw(canvas)
        pdfDocument.finishPage(page)

        // Directorio público para PDFs
        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val appDir = java.io.File(picturesDir, "NotebookPersonalized")
        if (!appDir.exists()) appDir.mkdirs()

        val fileName = "note_${System.currentTimeMillis()}.pdf"
        val file = java.io.File(appDir, fileName)
        return try {
            val fos = java.io.FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.flush()
            fos.close()
            pdfDocument.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    // Exportar a JSON
    fun exportToJson(context: Context): String? {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, Stroke::class.java)
        val adapter = moshi.adapter<List<Stroke>>(type)
        val json = adapter.toJson(strokes)
        // Guardar en archivo público
        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val appDir = java.io.File(picturesDir, "NotebookPersonalized")
        if (!appDir.exists()) appDir.mkdirs()
        val fileName = "note_${System.currentTimeMillis()}.json"
        val file = java.io.File(appDir, fileName)
        return try {
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

    fun addImageElement(bitmap: Bitmap, x: Float, y: Float, width: Float, height: Float) {
        imageElements.add(ImageElement(bitmap, x, y, width, height))
        invalidate()
    }
} 