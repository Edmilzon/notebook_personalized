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
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ToggleButton

class DrawingFragment : Fragment() {
    private lateinit var drawingCanvas: DrawingCanvasView
    private var isEraserOn = false
    private var pendingTextPosition: Pair<Float, Float>? = null
    private var pendingImagePosition: Pair<Float, Float>? = null
    private var noteName: String? = null
    private var autoSavePath: String? = null
    private enum class Tool { NONE, DRAW, ERASER, TEXT }
    private var activeTool: Tool = Tool.NONE
    private lateinit var contextualMenu: LinearLayout

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
        contextualMenu = view.findViewById(R.id.contextual_menu)

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
            if (activeTool == Tool.TEXT && event.action == MotionEvent.ACTION_DOWN) {
                showTextDialog(event.x, event.y)
                return@setOnTouchListener true
            }
            val handled = v.onTouchEvent(event)
            autoSaveNote()
            return@setOnTouchListener handled
        }

        // Lógica de selección de herramienta
        val btnDraw = view.findViewById<ImageButton>(R.id.btn_draw)
        val btnEraser = view.findViewById<ImageButton>(R.id.btn_eraser)
        val btnText = view.findViewById<ImageButton>(R.id.btn_text)
        val btnUndo = view.findViewById<ImageButton>(R.id.btn_undo)
        val btnRedo = view.findViewById<ImageButton>(R.id.btn_redo)
        val btnExport = view.findViewById<ImageButton>(R.id.btn_export)
        val btnImage = view.findViewById<ImageButton>(R.id.btn_image)
        val btnExit = view.findViewById<ImageButton>(R.id.btn_exit)

        btnDraw.setOnClickListener {
            setActiveTool(Tool.DRAW)
            drawingCanvas.setEraserMode(false)
        }
        btnEraser.setOnClickListener {
            setActiveTool(Tool.ERASER)
            drawingCanvas.setEraserMode(true)
        }
        btnText.setOnClickListener {
            setActiveTool(Tool.TEXT)
            drawingCanvas.setEraserMode(false)
        }
        btnUndo.setOnClickListener {
            drawingCanvas.undo()
        }
        btnRedo.setOnClickListener {
            drawingCanvas.redo()
        }
        btnExport.setOnClickListener {
            showSaveOptions()
        }
        btnImage.setOnClickListener {
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
        btnExit.setOnClickListener {
            saveEditableNote()
            findNavController().popBackStack()
        }

        // Manejar el back para volver siempre al dashboard
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setActiveTool(Tool.NONE)
                drawingCanvas.setEraserMode(false)
                findNavController().popBackStack()
            }
        })
    }

    private fun setActiveTool(tool: Tool) {
        activeTool = tool
        when (tool) {
            Tool.DRAW -> {
                showDrawOptions()
                drawingCanvas.setEraserMode(false)
            }
            Tool.ERASER -> {
                showEraserOptions()
                drawingCanvas.setEraserMode(true)
            }
            Tool.TEXT -> {
                showTextOptions()
                drawingCanvas.setEraserMode(false)
            }
            Tool.NONE -> {
                contextualMenu.visibility = View.GONE
                drawingCanvas.setEraserMode(false)
            }
        }
    }

    private fun showDrawOptions() {
        contextualMenu.removeAllViews()
        contextualMenu.visibility = View.VISIBLE
        // Selector de color
        val colors = arrayOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.WHITE)
        val colorNames = arrayOf("Negro", "Rojo", "Verde", "Azul", "Amarillo", "Blanco")
        val colorGroup = RadioGroup(requireContext())
        colorGroup.orientation = RadioGroup.HORIZONTAL
        colors.forEachIndexed { i, color ->
            val radio = RadioButton(requireContext())
            radio.text = colorNames[i]
            radio.setTextColor(color)
            radio.setOnClickListener { drawingCanvas.setColor(color) }
            colorGroup.addView(radio)
        }
        contextualMenu.addView(colorGroup)
        // Selector de grosor
        val seekBar = SeekBar(requireContext())
        seekBar.max = 50
        seekBar.progress = 10
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                drawingCanvas.setStrokeWidth(progress.toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        contextualMenu.addView(seekBar)
        // (Opcional) Selector de estilo de línea
        // ...
    }

    private fun showEraserOptions() {
        contextualMenu.removeAllViews()
        contextualMenu.visibility = View.VISIBLE
        // Selector de grosor
        val seekBar = SeekBar(requireContext())
        seekBar.max = 50
        seekBar.progress = 20
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                drawingCanvas.setStrokeWidth(progress.toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        contextualMenu.addView(seekBar)
        // Botón borrar todo
        val btnClear = ImageButton(requireContext())
        btnClear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        btnClear.setOnClickListener { drawingCanvas.clearCanvas() }
        contextualMenu.addView(btnClear)
        // (Opcional) Selector de tipo de borrador
        // ...
    }

    private fun showTextOptions() {
        contextualMenu.removeAllViews()
        contextualMenu.visibility = View.VISIBLE
        // Selector de color
        val colors = arrayOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.WHITE)
        val colorNames = arrayOf("Negro", "Rojo", "Verde", "Azul", "Amarillo", "Blanco")
        val colorGroup = RadioGroup(requireContext())
        colorGroup.orientation = RadioGroup.HORIZONTAL
        colors.forEachIndexed { i, color ->
            val radio = RadioButton(requireContext())
            radio.text = colorNames[i]
            radio.setTextColor(color)
            radio.setOnClickListener { drawingCanvas.setSelectedTextColor(color) }
            colorGroup.addView(radio)
        }
        contextualMenu.addView(colorGroup)
        // Selector de tamaño de letra
        val seekBar = SeekBar(requireContext())
        seekBar.max = 100
        seekBar.progress = 48
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                drawingCanvas.setSelectedTextSize(progress.toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        contextualMenu.addView(seekBar)
        // (Opcional) Selector de fuente
        // ...
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
        // Guardar automáticamente solo en formato editable (JSON), NO como imagen ni PDF
        val noteNameSafe = (noteName ?: "nota").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(requireContext().filesDir, "$noteNameSafe.json")
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(DrawingData::class.java)
        val data = drawingCanvas.getDrawingData(requireContext())
        val json = adapter.toJson(data)
        file.writeText(json)
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

    private fun showTextDialog(x: Float, y: Float) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(requireContext())
            .setTitle("Agregar texto")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val text = input.text.toString()
                // Usar color y tamaño actuales del menú contextual
                val color = getCurrentTextColorFromMenu() ?: Color.BLACK
                val size = getCurrentTextSizeFromMenu() ?: 48f
                drawingCanvas.addTextElement(text, x, y, color, size)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Helpers para obtener color/tamaño seleccionados en el menú contextual
    private fun getCurrentTextColorFromMenu(): Int? {
        for (i in 0 until contextualMenu.childCount) {
            val view = contextualMenu.getChildAt(i)
            if (view is RadioGroup) {
                for (j in 0 until view.childCount) {
                    val radio = view.getChildAt(j) as? RadioButton
                    if (radio?.isChecked == true) {
                        return radio.currentTextColor
                    }
                }
            }
        }
        return null
    }
    private fun getCurrentTextSizeFromMenu(): Float? {
        for (i in 0 until contextualMenu.childCount) {
            val view = contextualMenu.getChildAt(i)
            if (view is SeekBar) {
                return view.progress.toFloat()
            }
        }
        return null
    }
} 