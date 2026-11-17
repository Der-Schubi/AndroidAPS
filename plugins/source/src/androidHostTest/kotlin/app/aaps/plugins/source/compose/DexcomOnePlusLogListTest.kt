package app.aaps.plugins.source.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The driver log list: it opens at the newest line and follows the end while the person is there.
 *
 * The log is capped at 1000 lines, so a full log changes its lines without changing their number.
 * That case is the one these tests are built around.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RobolectricConfig(sdk = [35])
class DexcomOnePlusLogListTest {

    @get:Rule val compose = createComposeRule()

    /** [count] lines named `line <from>` .. `line <from + count - 1>`, oldest first. */
    private fun window(from: Int, count: Int = 1000) = (from until from + count).map { "line $it" }

    @Test
    fun `opens at the newest line`() {
        compose.setContent { DexcomOnePlusLogList(window(from = 1)) }

        compose.onNodeWithText("line 1000").assertIsDisplayed()
        compose.onNodeWithText("line 1").assertDoesNotExist()
    }

    @Test
    fun `follows new lines when the log is already full`() {
        var lines by mutableStateOf(window(from = 1))
        compose.setContent { DexcomOnePlusLogList(lines) }

        // Same number of lines, ten newer: what a full log looks like at the next read.
        compose.runOnIdle { lines = window(from = 11) }
        compose.waitForIdle()

        compose.onNodeWithText("line 1010").assertIsDisplayed()
    }

    @Test
    fun `stays where it is once the person has scrolled up`() {
        var lines by mutableStateOf(window(from = 1))
        compose.setContent { DexcomOnePlusLogList(lines) }

        compose.onNode(hasScrollAction()).performScrollToIndex(500)
        compose.waitForIdle()
        compose.runOnIdle { lines = window(from = 11) }
        compose.waitForIdle()

        compose.onNodeWithText("line 1010").assertDoesNotExist()
    }
}
