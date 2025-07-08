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
import android.widget.FrameLayout
import com.example.notebook_personalized.ui.drawing.DrawingCanvasView.Tool

class DrawingFragment : Fragment() {
    private lateinit var drawingCanvas: DrawingCanvasView
    private var isEraserOn = false
    private var pendingTextPosition: Pair<Float, Float>? = null
    private var pendingImagePosition: Pair<Float, Float>? = null
    private var noteName: String? = null
    private var autoSavePath: String? = null
    private lateinit var contextualMenu: LinearLayout
    private lateinit var btnDraw: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnText: ImageButton
    private lateinit var btnImage: ImageButton
    private var jsonPath: String? = null
    private var isImageInsertMode: Boolean = false

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
        isImageInsertMode = false
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
        val rootLayout = view as FrameLayout
        btnDraw = view.findViewById(R.id.btn_draw)
        btnEraser = view.findViewById(R.id.btn_eraser)
        btnText = view.findViewById(R.id.btn_text)
        btnImage = view.findViewById(R.id.btn_image)
        setActiveTool(Tool.TEXT)

        // Obtener el nombre de la nota si viene de los argumentos
        noteName = arguments?.getString("noteName")
        noteName?.let {
            activity?.title = it
        }

        // Cargar nota JSON si se abre desde la lista
        jsonPath = arguments?.getString("jsonPath")
        jsonPath?.let { path ->
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(DrawingData::class.java)
            val file = File(path)
            if (file.exists()) {
                val json = file.readText()
                val loadedData = adapter.fromJson(json)
                if (loadedData != null) {
                    drawingCanvas.setDrawingData(requireContext(), loadedData)
                }
            }
        }

        // Configurar el onTouchListener sin guardado automático
        drawingCanvas.setOnTouchListener { v, event ->
            when (activeTool) {
                Tool.TEXT -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        val color = getCurrentTextColorFromMenu() ?: Color.BLACK
                        val size = getCurrentTextSizeFromMenu() ?: 48f
                        drawingCanvas.startTextInput(event.x, event.y, color, size, rootLayout)
                        return@setOnTouchListener true
                    }
                }
                Tool.IMAGE -> {
                    if (isImageInsertMode) {
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            val touchedImage = drawingCanvas.findImageAt(event.x, event.y)
                            if (touchedImage == null) {
                                pendingImagePosition = Pair(event.x, event.y)
                                pickImageLauncher.launch("image/*")
                            } else {
                                Toast.makeText(requireContext(), "Toca un área vacía para agregar una imagen", Toast.LENGTH_SHORT).show()
                            }
                            return@setOnTouchListener true
                        }
                    } else {
                        return@setOnTouchListener v.onTouchEvent(event)
                    }
                }
                Tool.DRAW, Tool.ERASER -> {
                    val handled = v.onTouchEvent(event)
                    return@setOnTouchListener handled
                }
                else -> return@setOnTouchListener false
            }
            return@setOnTouchListener false
        }

        // Lógica de selección de herramienta
        val btnUndo = view.findViewById<ImageButton>(R.id.btn_undo)
        val btnRedo = view.findViewById<ImageButton>(R.id.btn_redo)
        val btnExport = view.findViewById<ImageButton>(R.id.btn_export)
        val btnExit = view.findViewById<ImageButton>(R.id.btn_exit)

        btnDraw.setOnClickListener {
            setActiveTool(Tool.DRAW)
        }
        btnEraser.setOnClickListener {
            setActiveTool(Tool.ERASER)
        }
        btnText.setOnClickListener {
            setActiveTool(Tool.TEXT)
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
            setActiveTool(Tool.IMAGE)
        }
        btnExit.setOnClickListener {
            saveEditableNote()
            findNavController().popBackStack()
        }

        // Manejar el back para volver siempre al dashboard
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setActiveTool(Tool.NONE)
                findNavController().popBackStack()
            }
        })
    }

    private var activeTool: Tool = Tool.NONE

    private fun setActiveTool(tool: Tool) {
        activeTool = tool
        drawingCanvas.setActiveTool(tool)
        drawingCanvas.closeActiveEditText()
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
            Tool.IMAGE -> {
                showImageOptions()
                drawingCanvas.setEraserMode(false)
            }
            Tool.NONE -> {
                contextualMenu.visibility = View.GONE
                drawingCanvas.setEraserMode(false)
            }
        }
        updateNavbarSelection()
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
        // Selector de color como cuadritos
        val colors = arrayOf(Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.WHITE)
        val colorGroup = LinearLayout(requireContext())
        colorGroup.orientation = LinearLayout.HORIZONTAL
        colors.forEach { color ->
            val colorBox = View(requireContext())
            val params = LinearLayout.LayoutParams(64, 64)
            params.setMargins(8, 8, 8, 8)
            colorBox.layoutParams = params
            colorBox.setBackgroundColor(color)
            colorBox.background = resources.getDrawable(R.drawable.color_box_bg, null)
            colorBox.setOnClickListener {
                drawingCanvas.setSelectedTextColor(color)
                // Marcar seleccionado visualmente
                for (i in 0 until colorGroup.childCount) {
                    colorGroup.getChildAt(i).alpha = 0.5f
                }
                colorBox.alpha = 1f
            }
            colorBox.alpha = 0.5f
            colorGroup.addView(colorBox)
        }
        if (colorGroup.childCount > 0) colorGroup.getChildAt(0).alpha = 1f
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

    private fun showImageOptions() {
        contextualMenu.removeAllViews()
        contextualMenu.visibility = View.VISIBLE
        // Botón para subir imagen
        val btnAddImage = ImageButton(requireContext())
        btnAddImage.setImageResource(android.R.drawable.ic_menu_add)
        btnAddImage.contentDescription = "Subir imagen"
        btnAddImage.setOnClickListener {
            isImageInsertMode = true
            Toast.makeText(requireContext(), "Toca un área vacía para agregar una imagen", Toast.LENGTH_SHORT).show()
        }
        contextualMenu.addView(btnAddImage)
        // Botón para recortar (placeholder)
        val btnCrop = ImageButton(requireContext())
        btnCrop.setImageResource(android.R.drawable.ic_menu_crop)
        btnCrop.contentDescription = "Recortar imagen"
        btnCrop.setOnClickListener {
            Toast.makeText(requireContext(), "Función de recorte próximamente", Toast.LENGTH_SHORT).show()
        }
        contextualMenu.addView(btnCrop)
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

    private fun saveEditableNote() {
        // Guardar en el archivo original si existe, si no crear uno nuevo
        val file = if (jsonPath != null) {
            File(jsonPath!!)
        } else {
            val noteNameSafe = (noteName ?: "nota_${System.currentTimeMillis()}").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            File(requireContext().filesDir, "$noteNameSafe.json")
        }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(DrawingData::class.java)
        val data = drawingCanvas.getDrawingData(requireContext())
        val json = adapter.toJson(data)
        file.writeText(json)
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

    private fun updateNavbarSelection() {
        val normalBg = android.R.color.transparent
        btnDraw.setBackgroundResource(if (activeTool == Tool.DRAW) R.drawable.navbar_btn_selected else normalBg)
        btnEraser.setBackgroundResource(if (activeTool == Tool.ERASER) R.drawable.navbar_btn_selected else normalBg)
        btnText.setBackgroundResource(if (activeTool == Tool.TEXT) R.drawable.navbar_btn_selected else normalBg)
        btnImage.setBackgroundResource(if (activeTool == Tool.IMAGE) R.drawable.navbar_btn_selected else normalBg)
    }
} 