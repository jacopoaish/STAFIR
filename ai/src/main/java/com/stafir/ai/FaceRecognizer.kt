package com.stafir.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Un volto trovato: riquadro (in frazioni 0..1 dell'immagine), impronta numerica e qualità. */
class DetectedFace(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val embedding: FloatArray,
    val quality: Float,
)

/**
 * Riconoscimento volti sul telefono:
 * 1) ML Kit trova i volti (rapido, nessun download);
 * 2) ogni volto viene raddrizzato e ritagliato a 160×160;
 * 3) FaceNet (TensorFlow Lite) calcola l'impronta a 128 numeri: due foto della stessa persona
 *    danno impronte simili. Nessuna immagine lascia il telefono.
 */
class FaceRecognizer(context: Context) : AutoCloseable {
    val id = "facenet-128-v1"
    val displayName = "Volti: ML Kit + FaceNet"
    val accelerator = "CPU (TFLite XNNPACK, 4 thread)"

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.05f)
            .build(),
    )
    private val lock = Mutex()
    private val interpreter: Interpreter? = try {
        val bytes = context.assets.open(MODEL).use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
        Interpreter(buf, Interpreter.Options().setNumThreads(4).setUseXNNPACK(true))
    } catch (e: Exception) {
        Log.w("FaceRecognizer", "Modello volti non disponibile: ${e.message}")
        null
    }
    private val outSize = interpreter?.getOutputTensor(0)?.shape()?.last() ?: 128
    private val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())

    /** false se il modello non è incluso in questa build (il resto dell'app funziona lo stesso). */
    val available: Boolean get() = interpreter != null

    suspend fun detect(bitmap: Bitmap): List<DetectedFace> {
        if (interpreter == null) return emptyList()
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val out = mutableListOf<DetectedFace>()
        for (f in faces) {
            val b = f.boundingBox
            val size = min(b.width(), b.height()).toFloat()
            val yaw = abs(f.headEulerAngleY)
            val pitch = abs(f.headEulerAngleX)
            // Volti troppo piccoli o di profilo danno impronte poco affidabili: meglio saltarli.
            if (size < MIN_FACE_PX || yaw > 40f || pitch > 30f) continue
            val crop = alignedCrop(bitmap, b.exactCenterX(), b.exactCenterY(), max(b.width(), b.height()).toFloat(), f.headEulerAngleZ)
            val emb = try { embed(crop) } finally { crop.recycle() }
            val quality = (size / max(w, h)).coerceAtMost(1f) * (1f - yaw / 90f)
            out += DetectedFace(
                (b.left / w).coerceIn(0f, 1f), (b.top / h).coerceIn(0f, 1f),
                (b.right / w).coerceIn(0f, 1f), (b.bottom / h).coerceIn(0f, 1f), emb, quality,
            )
        }
        return out
    }

    /** Ritaglio quadrato del volto, con un piccolo margine, raddrizzato se la testa è inclinata. */
    private fun alignedCrop(src: Bitmap, cx: Float, cy: Float, box: Float, roll: Float): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postTranslate(-cx, -cy)
            postRotate(roll)
            postScale(SIZE / (box * MARGIN), SIZE / (box * MARGIN))
            postTranslate(SIZE / 2f, SIZE / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private suspend fun embed(face: Bitmap): FloatArray = lock.withLock {
        val px = IntArray(SIZE * SIZE)
        face.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        // standardizzazione per immagine (come in addestramento FaceNet)
        var sum = 0.0
        var sq = 0.0
        for (p in px) {
            val r = (p shr 16 and 0xFF).toDouble(); val g = (p shr 8 and 0xFF).toDouble(); val bl = (p and 0xFF).toDouble()
            sum += r + g + bl; sq += r * r + g * g + bl * bl
        }
        val n = px.size * 3.0
        val mean = sum / n
        val std = max(sqrt(sq / n - mean * mean), 1.0 / sqrt(n))
        input.rewind()
        for (p in px) {
            input.putFloat((((p shr 16 and 0xFF) - mean) / std).toFloat())
            input.putFloat((((p shr 8 and 0xFF) - mean) / std).toFloat())
            input.putFloat((((p and 0xFF) - mean) / std).toFloat())
        }
        input.rewind()
        val output = Array(1) { FloatArray(outSize) }
        interpreter!!.run(input, output)
        val v = output[0]
        var s = 0f
        for (x in v) s += x * x
        val norm = sqrt(s).takeIf { it > 1e-6f } ?: 1f
        FloatArray(v.size) { v[it] / norm }
    }

    override fun close() {
        runCatching { detector.close() }
        runCatching { interpreter?.close() }
    }

    companion object {
        const val MODEL = "models/facenet.tflite"
        const val SIZE = 160
        const val MIN_FACE_PX = 48f
        const val MARGIN = 1.25f

        /** Il modello dei volti è incluso in questa installazione? */
        fun modelPresent(context: Context): Boolean =
            runCatching { context.assets.openFd(MODEL).use { it.length > 1_000_000 } }
                .recoverCatching { context.assets.open(MODEL).use { it.available() > 0 } }
                .getOrDefault(false)
    }
}
