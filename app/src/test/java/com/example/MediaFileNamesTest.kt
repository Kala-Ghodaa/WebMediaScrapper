package com.example

import com.example.data.download.MediaFileNames
import org.junit.Assert.*
import org.junit.Test

class MediaFileNamesTest {
    @Test fun `existing filename collision gets a distinct deterministic name`() {
        val first = MediaFileNames.choose("photo.jpg", "jpg", "https://a/photo.jpg", emptySet())
        val second = MediaFileNames.choose("photo.jpg", "jpg", "https://b/photo.jpg", setOf(first))
        assertNotEquals(first, second)
        assertEquals(second, MediaFileNames.choose("photo.jpg", "jpg", "https://b/photo.jpg", setOf(first)))
    }
    @Test fun `case insensitive filesystems do not overwrite existing names`() {
        assertNotEquals("PHOTO.jpg", MediaFileNames.choose("PHOTO.jpg", "jpg", "https://b", setOf("photo.JPG")))
    }
    @Test fun `untrusted names stay bounded and cannot escape their directory`() {
        val name = MediaFileNames.choose("../../" + "long".repeat(100), "jpg", "https://a", emptySet())
        assertFalse(name.contains('/')); assertFalse(name.startsWith('.')); assertTrue(name.length < 150)
        assertTrue(name.endsWith(".jpg"))
    }
}
