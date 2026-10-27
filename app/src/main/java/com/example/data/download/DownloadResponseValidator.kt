package com.example.data.download

/** Validate range responses before appending, so a resume cannot silently corrupt a file. */
internal object DownloadResponseValidator {
    data class Transfer(val append: Boolean, val totalBytes: Long, val expectedBodyBytes: Long)

    fun validate(code: Int, existingBytes: Long, contentLength: Long, contentRange: String?): Transfer {
        require(code in 200..299) { "HTTP $code" }
        if (code != 206) return Transfer(false, contentLength, contentLength)
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(contentRange.orEmpty())
            ?: error("Missing or invalid Content-Range for resumed download")
        val (start, end, total) = match.destructured.toList().map { it.toLong() }
        require(start == existingBytes && end >= start && total > end) { "Server returned an unexpected byte range" }
        val expected = end - start + 1
        require(contentLength < 0 || contentLength == expected) { "Content-Length does not match byte range" }
        // This downloader asks for the rest of the file, not a segmented transfer.
        require(end == total - 1) { "Server returned an incomplete byte range" }
        return Transfer(existingBytes > 0, total, expected)
    }

    fun requireComplete(writtenBytes: Long, transfer: Transfer) {
        require(writtenBytes > 0) { "Zero bytes received from server" }
        require(transfer.totalBytes < 0 || writtenBytes == transfer.totalBytes) { "Incomplete download: expected ${transfer.totalBytes}, received $writtenBytes bytes" }
    }
}
