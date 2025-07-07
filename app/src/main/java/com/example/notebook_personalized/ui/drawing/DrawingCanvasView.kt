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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor) // Asegura que el fondo sea blanco
        // Dibuja todos los paths guardados
        for ((path, paint) in paths) {
            canvas.drawPath(path, paint)
        }
        // Dibuja el path actual
        canvas.drawPath(currentPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // Guarda el path actual con una copia del Paint
                val newPaint = Paint(drawPaint)
                paths.add(Pair(currentPath, newPaint))
                currentPath = Path()
                // Al dibujar, se limpia la pila de deshacer
                undonePaths.clear()
                invalidate()
            }
        }
        return true
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
} 