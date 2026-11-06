package com.stafir.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * Riconoscimento di scene e oggetti (oltre 400 categorie: mare, montagna, cibo, animali,
 * documenti...). Modello ML Kit incluso nell'app: funziona senza internet e senza download.
 */
class MlKitSceneRecognizer(minConfidence: Float = 0.5f) : ImageRecognizer {
    override val id = "mlkit-labels-v1"
    override val displayName = "Scene e oggetti (ML Kit)"
    override val accelerator = "CPU (TFLite XNNPACK)"

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(minConfidence).build(),
    )

    override suspend fun recognize(bitmap: Bitmap): List<RecognizedLabel> =
        labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
            .map { RecognizedLabel(it.text.lowercase(), it.confidence) }

    override fun close() = labeler.close()
}
