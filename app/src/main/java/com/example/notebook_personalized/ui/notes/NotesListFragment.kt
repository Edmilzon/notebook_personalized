package com.example.notebook_personalized.ui.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notebook_personalized.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import android.widget.EditText
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController

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
            showCreateNoteDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun loadNotes() {
        val appDir = requireContext().filesDir
        notesList = appDir.listFiles()?.filter {
            it.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
        notesAdapter = NotesAdapter(notesList) { file ->
            openNote(file)
        }
        notesRecyclerView.adapter = notesAdapter
    }

    private fun showCreateNoteDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Nombre de la nota"
        AlertDialog.Builder(requireContext())
            .setTitle("Crear nueva nota")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val noteName = input.text.toString().ifBlank { "Nota sin título" }
                openNewNote(noteName)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openNewNote(noteName: String) {
        val args = Bundle()
        args.putString("noteName", noteName)
        findNavController().navigate(R.id.drawingFragment, args)
    }

    private fun openNote(file: File) {
        val args = Bundle()
        args.putString("jsonPath", file.absolutePath)
        findNavController().navigate(R.id.drawingFragment, args)
    }
} 