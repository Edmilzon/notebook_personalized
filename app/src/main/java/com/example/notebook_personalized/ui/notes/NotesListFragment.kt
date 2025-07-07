package com.example.notebook_personalized.ui.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notebook_personalized.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class NotesListFragment : Fragment() {
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var fabNewNote: FloatingActionButton
    private lateinit var notesAdapter: NotesAdapter
    private var notesList: List<File> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notes_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        notesRecyclerView = view.findViewById(R.id.notes_recycler_view)
        fabNewNote = view.findViewById(R.id.fab_new_note)

        notesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        notesAdapter = NotesAdapter(notesList) { file ->
            openNote(file)
        }
        notesRecyclerView.adapter = notesAdapter

        fabNewNote.setOnClickListener {
            // Navegar a la pizarra vacía (DrawingFragment)
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, com.example.notebook_personalized.ui.drawing.DrawingFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    fun loadNotes() {
        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "NotebookPersonalized")
        if (appDir.exists()) {
            notesList = appDir.listFiles()?.filter {
                it.name.endsWith(".png") || it.name.endsWith(".pdf") || it.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            notesList = emptyList()
        }
        notesAdapter = NotesAdapter(notesList) { file ->
            openNote(file)
        }
        notesRecyclerView.adapter = notesAdapter
    }

    private fun openNote(file: File) {
        when {
            file.name.endsWith(".png") || file.name.endsWith(".pdf") -> {
                // Abrir con visor externo
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.fromFile(file), if (file.name.endsWith(".png")) "image/*" else "application/pdf")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, "Abrir nota"))
            }
            file.name.endsWith(".json") -> {
                // Abrir en la pizarra para edición
                val fragment = com.example.notebook_personalized.ui.drawing.DrawingFragment()
                val args = Bundle()
                args.putString("jsonPath", file.absolutePath)
                fragment.arguments = args
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }
} 