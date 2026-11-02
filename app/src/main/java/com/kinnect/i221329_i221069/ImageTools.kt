package com.kinnect.i221329_i221069

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

// Everything about turning a picture into text and back. Kinnect stores pictures inside Firestore
// documents as shrunken Base64 JPEGs, so there is no Firebase Storage to set up (it needs a paid plan).
// Reading and shrinking happens on a background thread; the result is handed back on the main thread.

/** A picture ready to be stored: a full size version and a small one for lists. */
class EncodedPicture(val full: String, val thumb: String)

object ImageTools {

    // Filters of the story editor (0 = none)
    const val FILTER_NONE = 0
    const val FILTER_MONO = 1
    const val FILTER_WARM = 2
    const val FILTER_COOL = 3
    const val FILTER_VIVID = 4
    val FILTER_NAMES = arrayOf("Original", "Mono", "Warm", "Cool", "Vivid")

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** Reads the picture at [uri], upright and no bigger than [maxSide] pixels on its longest side. */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.applicationContext.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap == null) {
                null
            } else {
                // Gallery photos are often stored sideways with a rotation flag
                val orientation = resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
                if (scale < 1f) {
                    matrix.postScale(scale, scale)
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The colour matrix of a filter, or null for the original picture. */
    fun matrixOf(filter: Int): ColorMatrix? {
        val matrix = ColorMatrix()
        return when (filter) {
            FILTER_MONO -> matrix.apply { setSaturation(0f) }
            FILTER_WARM -> matrix.apply { setScale(1.12f, 1.0f, 0.86f, 1f) }
            FILTER_COOL -> matrix.apply { setScale(0.88f, 1.0f, 1.12f, 1f) }
            FILTER_VIVID -> matrix.apply { setSaturation(1.6f) }
            else -> null
        }
    }

    /** A copy of [source] with the filter painted in (the original when there is no filter). */
    fun applyFilter(source: Bitmap, filter: Int): Bitmap {
        val matrix = matrixOf(filter) ?: return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** JPEG as Base64 text, compressed harder until it fits in [maxBytes]. */
    fun toBase64(bitmap: Bitmap, maxBytes: Int): String {
        var quality = 80
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 15
        } while (bytes.size > maxBytes && quality > 10)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Base64 text back to a picture (null when it is not a picture). */
    fun fromBase64(text: String): Bitmap? {
        return try {
            val bytes = Base64.decode(text, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /** Reads, shrinks and encodes one picture. [done] gets null when the picture cannot be read. */
    fun encode(context: Context, uri: Uri, maxSide: Int, maxBytes: Int, filter: Int, done: (String?) -> Unit) {
        worker.execute {
            val bitmap = decode(context, uri, maxSide)
            val result = if (bitmap == null) null else toBase64(applyFilter(bitmap, filter), maxBytes)
            main.post { done(result) }
        }
    }

    /** Like [encode] but also makes the small version used in lists (stories row). */
    fun encodePicture(context: Context, uri: Uri, filter: Int, done: (EncodedPicture?) -> Unit) {
        worker.execute {
            val bitmap = decode(context, uri, 1080)
            val result = if (bitmap == null) {
                null
            } else {
                val filtered = applyFilter(bitmap, filter)
                val scale = 360f / maxOf(filtered.width, filtered.height)
                val small = if (scale < 1f) {
                    Bitmap.createScaledBitmap(filtered, (filtered.width * scale).toInt(), (filtered.height * scale).toInt(), true)
                } else {
                    filtered
                }
                EncodedPicture(toBase64(filtered, 600_000), toBase64(small, 40_000))
            }
            main.post { done(result) }
        }
    }

    /** A video longer than about 10 seconds does not fit into the database as text. */
    const val MAX_VIDEO_BYTES = 4_500_000

    /** The first picture of a video (null when it cannot be read). */
    fun videoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) {
                null
            } else {
                val scale = 1080f / maxOf(frame.width, frame.height)
                if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true) else frame
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Reads a video clip and turns it into Base64 text; [EncodedPicture.thumb] is a small picture of its first frame.
     * [done] gets null when the clip is too big (more than about 10 seconds) or cannot be read.
     */
    fun encodeVideo(context: Context, uri: Uri, done: (EncodedPicture?) -> Unit) {
        worker.execute {
            val result = try {
                val bytes = context.applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val frame = videoFrame(context, uri)
                if (bytes == null || frame == null || bytes.size > MAX_VIDEO_BYTES) {
                    null
                } else {
                    val scale = 360f / maxOf(frame.width, frame.height)
                    val small = if (scale < 1f) {
                        Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                    } else {
                        frame
                    }
                    EncodedPicture(Base64.encodeToString(bytes, Base64.NO_WRAP), toBase64(small, 40_000))
                }
            } catch (e: Exception) {
                null
            }
            main.post { done(result) }
        }
    }

    /** Reads a picture for showing on screen only (no encoding). [done] runs on the main thread. */
    fun load(context: Context, uri: Uri, maxSide: Int, done: (Bitmap?) -> Unit) {
        worker.execute {
            val bitmap = decode(context, uri, maxSide)
            main.post { done(bitmap) }
        }
    }
}
