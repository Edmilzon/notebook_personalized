package com.example.notebook_personalized.data.model

data class DrawingData(
    val strokes: List<Stroke>,
    val texts: List<TextElement>,
    val images: List<ImageElementSerializable>
)

data class ImageElementSerializable(
    val imagePath: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) 