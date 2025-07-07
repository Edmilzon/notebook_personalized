package com.example.notebook_personalized.data.model

data class Drawing(
    val id: Long = 0,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis()
) 