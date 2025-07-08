package com.example.notebook_personalized.data.model

import android.graphics.Bitmap

data class ImageElement(
    var bitmap: Bitmap,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var originalPath: String? = null
) 