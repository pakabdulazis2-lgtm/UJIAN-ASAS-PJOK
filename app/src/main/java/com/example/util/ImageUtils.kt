package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun uriToCompressedDataUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxBytes: Int = 210_000
    ): Result<String> {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            var sampleSize = 1
            while (
                boundsOpts.outWidth / sampleSize > maxDimension * 2 ||
                boundsOpts.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return Result.failure(IllegalStateException("Gagal membaca file gambar."))

            val scale = minOf(
                maxDimension.toFloat() / rawBitmap.width.coerceAtLeast(1),
                maxDimension.toFloat() / rawBitmap.height.coerceAtLeast(1),
                1f
            )
            val targetW = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
            } else {
                rawBitmap
            }

            var quality = 85
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 12
            } while (bytes.size > maxBytes && quality >= 25)

            if (scaled != rawBitmap) rawBitmap.recycle()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success("data:image/jpeg;base64,$encoded")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal memproses gambar: ${e.localizedMessage}"))
        }
    }

    fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
        return try {
            val base64Part = dataUri.substringAfter("base64,", "")
            if (base64Part.isBlank()) return null
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun SmartImage(
    imageData: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    if (imageData.startsWith("data:image/")) {
        val bitmap = remember(imageData) {
            ImageUtils.decodeDataUriToBitmap(imageData)?.asImageBitmap()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    } else {
        AsyncImage(
            model = imageData,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
