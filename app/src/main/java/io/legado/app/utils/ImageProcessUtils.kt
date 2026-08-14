package io.legado.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageProcessUtils {

    fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        aspectWidth: Int,
        aspectHeight: Int,
        dirName: String,
        prefix: String,
        targetWidth: Int = 1600,
        outputPath: String? = null
    ): String? {
        val safeAspectWidth = aspectWidth.coerceAtLeast(1)
        val safeAspectHeight = aspectHeight.coerceAtLeast(1)
        val safeTargetWidth = targetWidth.coerceAtLeast(128)
        val targetHeight = (safeTargetWidth * safeAspectHeight.toFloat() / safeAspectWidth)
            .roundToInt()
            .coerceAtLeast(128)
        val scaled = if (bitmap.width != safeTargetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, safeTargetWidth, targetHeight, true)
        } else {
            bitmap
        }
        val extension = scaled.preferredCoverExtension()
        val file = if (outputPath.isNullOrBlank()) {
            val dir = context.externalFiles.getFile(dirName).apply { mkdirs() }
            File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
        } else {
            File(outputPath).withExtension(extension).apply {
                parentFile?.mkdirs()
            }
        }
        FileOutputStream(file).use {
            scaled.compressPreservingAlpha(it, 92)
        }
        if (scaled !== bitmap) scaled.recycle()
        return file.absolutePath
    }

    private fun File.withExtension(extension: String): File {
        if (this.extension.equals(extension, ignoreCase = true)) {
            return this
        }
        return File(parentFile, "$nameWithoutExtension.$extension")
    }

    fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= targetWidth &&
            height / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }
}


fun Bitmap.hasTransparentPixels(): Boolean {
    if (!hasAlpha() || width <= 0 || height <= 0 || isRecycled) return false
    return runCatching {
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount <= TRANSPARENT_PIXEL_FULL_SCAN_LIMIT) {
            val row = IntArray(width)
            for (y in 0 until height) {
                getPixels(row, 0, width, 0, y, width, 1)
                for (pixel in row) {
                    if (Color.alpha(pixel) < 255) return true
                }
            }
            return false
        }
        val step = ceil(sqrt(pixelCount.toDouble() / TRANSPARENT_PIXEL_FULL_SCAN_LIMIT)).toInt()
            .coerceAtLeast(2)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (Color.alpha(getPixel(x, y)) < 255) return true
                x += step
            }
            y += step
        }
        false
    }.getOrDefault(hasAlpha())
}

private const val TRANSPARENT_PIXEL_FULL_SCAN_LIMIT = 512_000L

fun Bitmap.preferredCoverExtension(): String {
    return if (hasTransparentPixels()) "png" else "jpg"
}

fun Bitmap.compressPreservingAlpha(
    outputStream: OutputStream,
    jpegQuality: Int = 90
): Boolean {
    val usePng = hasTransparentPixels()
    return compress(
        if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
        if (usePng) 100 else jpegQuality,
        outputStream
    )
}
