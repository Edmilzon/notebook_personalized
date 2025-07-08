package com.example.notebook_personalized.ui.notes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.notebook_personalized.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
        private val date: TextView = itemView.findViewById(R.id.note_date)

        fun bind(file: File) {
            title.text = file.nameWithoutExtension
            date.text = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            if (file.name.endsWith(".png")) {
                Glide.with(itemView).load(file).centerCrop().into(icon)
            } else if (file.name.endsWith(".pdf")) {
                icon.setImageResource(R.drawable.ic_pdf)
            } else if (file.name.endsWith(".json")) {
                icon.setImageResource(R.drawable.ic_note)
            } else {
                icon.setImageResource(R.drawable.ic_file)
            }
            itemView.setOnClickListener { onNoteClick(file) }
        }
    }
} 