package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.MediaType
import com.example.ui.MainUiState
import com.example.ui.components.TelegramFolderShareSheet
import com.example.ui.screens.DetectedMediaScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class TelegramSheetInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `empty folder cannot start a share`() {
        var calls = 0
        compose.setContent { MyApplicationTheme {
            TelegramFolderShareSheet("Empty", emptyList(), null, { _, _, _ -> calls++ }, {}, {}, {})
        } }
        compose.onNodeWithTag("telegram_empty_state").assertIsDisplayed()
        compose.onNodeWithTag("share_entire_folder_telegram_button").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("share_entire_folder_chooser_button").assertIsNotEnabled()
        assertEquals(0, calls)
    }

    @Test fun `parts contain only exact completed tasks and sheet stays available for next part`() {
        val tasks = (1..11).map { sampleTask("$it") } + sampleTask("pending", status = DownloadStatus.PAUSED)
        val batches = mutableListOf<List<DownloadTask>>()
        val captions = mutableListOf<String>()
        var dismissed = false
        compose.setContent { MyApplicationTheme {
            TelegramFolderShareSheet("Trip", tasks, null, { items, caption, _ -> batches.add(items); captions.add(caption) },
                {}, {}, { dismissed = true })
        } }
        compose.onNodeWithTag("share_telegram_chunk_1").performScrollTo().performClick()
        compose.onNodeWithTag("share_telegram_chunk_2").performScrollTo().performClick()
        assertEquals((1..10).map { "$it" }, batches[0].map { it.id })
        assertEquals(listOf("11"), batches[1].map { it.id })
        assertTrue(captions[0].contains("Part 1/2")); assertTrue(captions[1].contains("Part 2/2"))
        assertFalse(dismissed)
        compose.onNodeWithTag("share_entire_folder_telegram_button").assertDoesNotExist()
    }

    @Test fun `preparing prevents duplicate share requests`() {
        compose.setContent { MyApplicationTheme {
            TelegramFolderShareSheet("Trip", listOf(sampleTask("1")), null, { _, _, _ -> fail("Duplicate share") },
                {}, {}, {}, isPreparing = true)
        } }
        compose.onNodeWithTag("telegram_preparing_progress").assertIsDisplayed()
        compose.onNodeWithTag("share_entire_folder_telegram_button").assertIsNotEnabled()
        compose.onNodeWithTag("share_entire_folder_chooser_button").assertIsNotEnabled()
    }

    @Test fun `large text keeps share parts reachable`() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            var shared = 0
            compose.setContent { MyApplicationTheme(darkTheme = false) {
                TelegramFolderShareSheet("A very long travel collection title for accessibility", (1..11).map { sampleTask("$it") },
                    "https://example.com/album", { tasks, _, _ -> shared = tasks.size }, {}, {}, {})
            } }
            compose.onNodeWithTag("share_telegram_chunk_2").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(1, shared)
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun `hidden selected ids do not make visible media appear all selected`() {
        val item = com.example.domain.model.DetectedMediaItem("visible", "https://example.com/a.jpg", "https://example.com/a.jpg",
            "https://example.com", title = "a.jpg", mediaType = MediaType.IMAGE, extension = "jpg", mimeType = "image/jpeg")
        var select = false
        var deselect = false
        compose.setContent { MyApplicationTheme {
            DetectedMediaScreen(MainUiState(filteredMediaItems = listOf(item), selectedIds = setOf("hidden")), {}, {}, {}, {}, {}, {}, {},
                { select = true }, { deselect = true }, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("select_all_button").performClick()
        assertTrue(select); assertFalse(deselect)
    }
}
