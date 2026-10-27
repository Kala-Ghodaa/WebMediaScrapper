package com.example

import com.example.data.download.DownloadResponseValidator
import org.junit.Assert.*
import org.junit.Test

class DownloadResponseValidatorTest {
    @Test fun `server ignoring range restarts instead of appending`() {
        val transfer = DownloadResponseValidator.validate(200, 500, 1000, null)
        assertFalse(transfer.append)
        assertEquals(1000, transfer.totalBytes)
        DownloadResponseValidator.requireComplete(1000, transfer)
    }
    @Test fun `valid resume appends exact remaining range`() {
        val transfer = DownloadResponseValidator.validate(206, 500, 500, "bytes 500-999/1000")
        assertTrue(transfer.append)
        DownloadResponseValidator.requireComplete(1000, transfer)
    }
    @Test fun `wrong offset missing range and partial segment cannot corrupt a resumed file`() {
        for (range in listOf(null, "garbage", "bytes 0-499/1000", "bytes 500-749/1000", "bytes 500-1000/1000")) {
            assertTrue("Range $range should fail", runCatching {
                DownloadResponseValidator.validate(206, 500, 500, range)
            }.isFailure)
        }
    }
    @Test fun `short empty and mismatched bodies cannot complete`() {
        val transfer = DownloadResponseValidator.validate(200, 0, 1000, null)
        assertThrows(IllegalArgumentException::class.java) { DownloadResponseValidator.requireComplete(999, transfer) }
        assertThrows(IllegalArgumentException::class.java) { DownloadResponseValidator.requireComplete(0, transfer) }
        assertThrows(IllegalArgumentException::class.java) { DownloadResponseValidator.validate(206, 500, 400, "bytes 500-999/1000") }
    }
    @Test fun `unknown content length still requires nonempty bytes`() {
        DownloadResponseValidator.requireComplete(123, DownloadResponseValidator.validate(200, 0, -1, null))
    }
}
