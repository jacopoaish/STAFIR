package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceClassifierTest {
    @Test fun classifiesCommonPaths() {
        assertEquals(Source.CAMERA, SourceClassifier.classify("DCIM/Camera/", "IMG_20240812_101010.jpg"))
        assertEquals(Source.WHATSAPP, SourceClassifier.classify("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "IMG-20240101-WA0001.jpg"))
        assertEquals(Source.WHATSAPP, SourceClassifier.classify("Pictures/", "IMG-20240101-WA0001.jpg"))
        assertEquals(Source.SCREENSHOT, SourceClassifier.classify("DCIM/Screenshots/", "Screenshot_2024-01-01-10-00-00-123_com.app.jpg"))
        assertEquals(Source.DOWNLOAD, SourceClassifier.classify("Download/", "file.jpg"))
        assertEquals(Source.TELEGRAM, SourceClassifier.classify("Pictures/Telegram/", "photo.jpg"))
        assertEquals(Source.SCREEN_RECORDING, SourceClassifier.classify("DCIM/ScreenRecorder/", "rec.mp4"))
        assertEquals(Source.OTHER, SourceClassifier.classify("Pictures/Varie/", "foto.jpg"))
    }
}
