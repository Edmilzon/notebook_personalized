package com.example.notebook_personalized.ui.drawing

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Toast
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.notebook_personalized.R
import com.example.notebook_personalized.data.model.TextElement
import com.example.notebook_personalized.data.model.Stroke
import com.example.notebook_personalized.data.model.DrawingData
import com.example.notebook_personalized.ui.MainActivity
import com.example.notebook_personalized.ui.drawing.DrawingCanvasView
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DrawingFragment : Fragment() {
    private lateinit var drawingCanvas: DrawingCanvasView
    private var isEraserOn = false
    private var pendingTextPosition: Pair<Float, Float>? = null
    private var pendingImagePosition: Pair<Float, Float>? = null
    private var noteName: String? = null
    private var autoSavePath: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val inputStream = requireContext().contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            pendingImagePosition?.let { pos ->
                drawingCanvas.addImageElement(bitmap, pos.first, pos.second, 300f, 300f)
                pendingImagePosition = null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drawing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        drawingCanvas = view.findViewById(R.id.drawing_canvas)

        // Obtener el nombre de la nota si viene de los argumentos
        noteName = arguments?.getString("noteName")
        noteName?.let {
            activity?.title = it
        }

        // Cargar nota JSON si se abre desde la lista
        arguments?.getString("jsonPath")?.let { jsonPath ->
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(DrawingData::class.java)
            val file = File(jsonPath)
            if (file.exists()) {
                val json = file.readText()
                val loadedData = adapter.fromJson(json)
                if (loadedData != null) {
                    drawingCanvas.setDrawingData(requireContext(), loadedData)
                }
            }
        }

        // Guardado automático cada vez que se dibuja o edita
        drawingCanvas.setOnTouchListener { v, event ->
            val handled = v.onTouchEvent(event)
            autoSaveNote()
            return@setOnTouchListener handled
        }

        view.findViewById<ImageButton>(R.id.btn_color).setOnClickListener {
            showColorPicker()
        }
        view.findViewById<ImageButton>(R.id.btn_stroke).setOnClickListener {
            showStrokePicker()
        }
        view.findViewById<ImageButton>(R.id.btn_eraser).setOnClickListener {
            isEraserOn = !isEraserOn
            drawingCanvas.setEraserMode(isEraserOn)
            Toast.makeText(requireContext(), if (isEraserOn) "Borrador activado" else "Borrador desactivado", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<ImageButton>(R.id.btn_undo).setOnClickListener {
            drawingCanvas.undo()
        }
        view.findViewById<ImageButton>(R.id.btn_redo).setOnClickListener {
            drawingCanvas.redo()
        }
        view.findViewById<ImageButton>(R.id.btn_clear).setOnClickListener {
            drawingCanvas.clearCanvas()
        }
        view.findViewById<ImageButton>(R.id.btn_save).setOnClickListener {
            showSaveOptions()
        }
        view.findViewById<ImageButton>(R.id.btn_text).setOnClickListener {
            Toast.makeText(requireContext(), "Toca la pizarra para colocar el texto", Toast.LENGTH_SHORT).show()
            drawingCanvas.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showTextDialog(event.x, event.y)
                    drawingCanvas.setOnTouchListener(null)
                    return@setOnTouchListener true
                }
                false
            }
        }
        view.findViewById<ImageButton>(R.id.btn_image).setOnClickListener {
            Toast.makeText(requireContext(), "Toca la pizarra para colocar la imagen", Toast.LENGTH_SHORT).show()
            drawingCanvas.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    pendingImagePosition = Pair(event.x, event.y)
                    pickImageLauncher.launch("image/*")
                    drawingCanvas.setOnTouchListener(null)
                    return@setOnTouchListener true
                }
                false
            }
        }
        // Editar texto al tocarlo
        drawingCanvas.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val selected = getSelectedTextElement(event.x, event.y)
                if (selected != null) {
                    showEditTextDialog(selected)
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Manejar el back para volver siempre al dashboard
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
            }
        })

        // Botón de salir (check)
        view.findViewById<ImageButton>(R.id.btn_exit).setOnClickListener {
            saveEditableNote()
            findNavController().popBackStack()
        }
    }

    private fun showColorPicker() {
        val colors = arrayOf("Negro", "Rojo", "Verde", "Azul", "Amarillo")
        val colorValues = arrayOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        AlertDialog.Builder(requireContext())
            .setTitle("Selecciona un color")
            .setItems(colors) { _, which ->
                drawingCanvas.setColor(colorValues[which])
                isEraserOn = false
                drawingCanvas.setEraserMode(false)
            }
            .show()
    }

    private fun showStrokePicker() {
        val seekBar = SeekBar(requireContext())
        seekBar.max = 50
        seekBar.progress = 10
        AlertDialog.Builder(requireContext())
            .setTitle("Selecciona el grosor")
            .setView(seekBar)
            .setPositiveButton("OK") { _, _ ->
                drawingCanvas.setStrokeWidth(seekBar.progress.toFloat())
            }
            .show()
    }

    private fun showSaveOptions() {
        val options = arrayOf("Guardar como Imagen", "Exportar a PDF")
        AlertDialog.Builder(requireContext())
            .setTitle("Guardar/Exportar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val path = drawingCanvas.saveToGallery(requireContext())
                        Toast.makeText(requireContext(), if (path != null) "Imagen guardada en $path" else "Error al guardar imagen", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val path = drawingCanvas.saveToPdf(requireContext())
                        Toast.makeText(requireContext(), if (path != null) "PDF guardado en $path" else "Error al exportar PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun autoSaveNote() {
        // Guardar automáticamente como imagen (y PDF si quieres)
        autoSavePath = drawingCanvas.saveToGallery(requireContext())
        // Si quieres también PDF: drawingCanvas.saveToPdf(requireContext())
    }

    private fun showTextDialog(x: Float, y: Float) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(requireContext())
            .setTitle("Agregar texto")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val text = input.text.toString()
                drawingCanvas.addTextElement(text, x, y)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditTextDialog(textElement: TextElement) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(textElement.text)
        AlertDialog.Builder(requireContext())
            .setTitle("Editar texto")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                drawingCanvas.editSelectedText(input.text.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun getSelectedTextElement(x: Float, y: Float): TextElement? {
        // Busca si hay un texto seleccionado en esa posición
        val textElementsField = DrawingCanvasView::class.java.getDeclaredField("textElements")
        textElementsField.isAccessible = true
        val textElements = textElementsField.get(drawingCanvas) as List<TextElement>
        for (txt in textElements.reversed()) {
            val paint = Paint().apply {
                textSize = txt.textSize
            }
            val bounds = Rect()
            paint.getTextBounds(txt.text, 0, txt.text.length, bounds)
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

    private fun saveEditableNote() {
        val noteNameSafe = (noteName ?: "nota").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(requireContext().filesDir, "$noteNameSafe.json")
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(DrawingData::class.java)
        val data = drawingCanvas.getDrawingData(requireContext())
        val json = adapter.toJson(data)
        file.writeText(json)
    }
} 