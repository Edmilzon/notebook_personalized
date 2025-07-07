package com.example.notebook_personalized.ui.notes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.notebook_personalized.R
import java.io.File

class NotesAdapter(
    private val notes: List<File>,
    private val onNoteClick: (File) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount(): Int = notes.size

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.note_icon)
        private val title: TextView = itemView.findViewById(R.id.note_title)
        private val subtitle: TextView = itemView.findViewById(R.id.note_subtitle)

        fun bind(file: File) {
            title.text = file.name
            subtitle.text = when {
                file.name.endsWith(".png") -> "Imagen"
                file.name.endsWith(".pdf") -> "PDF"
                file.name.endsWith(".json") -> "Nota editable"
                else -> "Archivo"
            }
            icon.setImageResource(
                when {
                    file.name.endsWith(".png") -> R.drawable.ic_image
                    file.name.endsWith(".pdf") -> R.drawable.ic_pdf
                    file.name.endsWith(".json") -> R.drawable.ic_note
                    else -> R.drawable.ic_file
                }
            )
            itemView.setOnClickListener { onNoteClick(file) }
        }
    }
} 