package com.example.notebook_personalized.data.model

data class TextElement(
    var text: String,
    var x: Float,
    var y: Float,
    var color: Int = 0xFF000000.toInt(),
    var textSize: Float = 48f,
    var isSelected: Boolean = false
) 