package com.enverolab.comandas.io

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Limita memoria incluso al cargar imágenes de respaldos antiguos. */
fun logoImage(path: String): Bitmap? {
    if (path.isBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
