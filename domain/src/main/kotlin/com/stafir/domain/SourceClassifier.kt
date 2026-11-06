package com.stafir.domain

/** Capisce la provenienza di un file dal percorso relativo (es. "DCIM/Camera/") e dal nome. */
object SourceClassifier {
    fun classify(relativePath: String?, displayName: String?): Source {
        val p = (relativePath ?: "").lowercase()
        val n = (displayName ?: "").lowercase()
        return when {
            "whatsapp" in p || n.matches(Regex("^(img|vid)-\\d{8}-wa\\d+.*")) -> Source.WHATSAPP
            "telegram" in p -> Source.TELEGRAM
            listOf("signal", "messenger", "viber", "wechat", "line/", "threema").any { it in p } -> Source.MESSAGING
            "screenrecord" in p || "screen record" in p || "registrazion" in p -> Source.SCREEN_RECORDING
            "screenshot" in p || n.startsWith("screenshot") -> Source.SCREENSHOT
            "download" in p -> Source.DOWNLOAD
            listOf("instagram", "facebook", "tiktok", "snapchat", "twitter", "pinterest").any { it in p } -> Source.SOCIAL
            p.startsWith("dcim/camera") || p.startsWith("dcim/100") || p == "dcim/" ||
                n.matches(Regex("^(img|vid|pxl|mvimg)_\\d{8}_\\d+.*")) -> Source.CAMERA
            "edited" in p || "editor" in p || "snapseed" in p || "lightroom" in p -> Source.EDITED
            else -> Source.OTHER
        }
    }
}
