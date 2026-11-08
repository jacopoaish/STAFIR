package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FileNameDatesTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test fun cameraNames() {
        assertEquals(ms(2024, 5, 2, 17, 30, 0), FileNameDates.parse("IMG_20240502_173000.jpg", zone))
        assertEquals(ms(2024, 5, 2, 17, 30, 0), FileNameDates.parse("VID_20240502_173000.mp4", zone))
        assertEquals(ms(2024, 1, 1, 12, 34, 56), FileNameDates.parse("PXL_20240101_123456789.jpg", zone))
        assertEquals(ms(2024, 5, 2, 17, 30, 0), FileNameDates.parse("20240502_173000.jpg", zone))
    }

    @Test fun messagingAndScreenshots() {
        assertEquals(ms(2024, 1, 10, 12, 0, 0), FileNameDates.parse("IMG-20240110-WA0003.jpg", zone))
        assertEquals(ms(2024, 5, 2, 17, 30, 0), FileNameDates.parse("Screenshot_2024-05-02-17-30-00-123_com.whatsapp.jpg", zone))
        assertEquals(ms(2024, 1, 10, 12, 30, 45), FileNameDates.parse("photo_2024-01-10_12-30-45.jpg", zone))
    }

    @Test fun invalidOrMissing() {
        assertNull(FileNameDates.parse("vacanze.jpg", zone))
        assertNull(FileNameDates.parse("IMG_99999999_999999.jpg", zone))
        assertNull(FileNameDates.parse(null, zone))
        assertNull(FileNameDates.parse("DSC01234.JPG", zone))
    }

    @Test fun epochMillisNames() {
        assertEquals(1714665000123L, FileNameDates.parse("1714665000123.jpg", zone))
    }
}
