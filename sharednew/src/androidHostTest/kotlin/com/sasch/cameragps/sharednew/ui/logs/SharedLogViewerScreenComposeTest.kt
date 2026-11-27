package com.sasch.cameragps.sharednew.ui.logs

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sasch.cameragps.sharednew.database.logging.LogRepository
import com.sasch.cameragps.sharednew.logging.LogFormatter
import com.sasch.cameragps.sharednew.ui.theme.CameraGpsTheme
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedLogViewerScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clearConfirmationHidesTheLogPageUntilDismissedAndClearsOnlyOnConfirm() {
        val repository = LogRepository()
        repository.insertLog(1, 4, null, "A log entry", null)
        val formatter = object : LogFormatter {
            override fun format() = repository.getRecentLogs().map { logs -> logs.map { it.message } }
        }
        compose.setContent {
            CameraGpsTheme {
                SharedLogViewerScreen(formatter, repository, onBackClick = {})
            }
        }

        val logEntry = compose.onNodeWithText("A log entry")
        val clear = compose.onNodeWithContentDescription("Clear all logs")
        logEntry.assertExists()
        clear.performClick()
        compose.onNodeWithText("Clear all logs? This cannot be undone.").assertExists()
        logEntry.assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        logEntry.assertExists()

        clear.performClick()
        clear.assertDoesNotExist()
        compose.onNode(hasText("Clear all logs") and hasClickAction()).performClick()
        compose.onNodeWithText("No logs available yet.", substring = true).assertExists()
    }
}
