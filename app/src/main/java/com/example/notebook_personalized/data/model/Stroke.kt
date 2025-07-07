package com.example.notebook_personalized.data.model

data class PointF(val x: Float, val y: Float)

data class Stroke(
    val points: List<PointF>,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false
) 