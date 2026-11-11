package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.SourceStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** How often the log screen reads the lines again while it is open. */
private const val LOG_REFRESH_MILLIS = 5_000L

/**
 * What the ONE+ / G7 driver wrote, read only.
 *
 * The lines come from [DexcomOnePlusHost.driverLog], the driver lines of `AndroidAPS.log`.
 * [DexcomOnePlusHost.DriverLog.note] says where they come from, so a person knows what they are
 * reading. The list opens at the newest line, follows the end of the log while the person is
 * there and stops following once they scroll up (see [DexcomOnePlusLogList]).
 *
 * [reloads] is bumped by the refresh button in the toolbar.
 *
 * The idea follows `G7CommLog`, the communication log of the G7 plugin tab in nightscout/AndroidAPS
 * pull request 5397 (Dexcom G7 direct, by t1dude, commit `bec1b16ba20`), written again on this
 * driver. See the NOTICE file of `:plugins:dexcom_oneplus`.
 */
@Composable
fun DexcomOnePlusLogScreen(
    host: DexcomOnePlusHost,
    reloads: Int,
    onLinesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var log by remember { mutableStateOf<DexcomOnePlusHost.DriverLog?>(null) }

    LaunchedEffect(reloads) {
        while (true) {
            val read = host.driverLog()
            log = read
            onLinesChanged(read.lines)
            delay(LOG_REFRESH_MILLIS)
        }
    }
    val lines = log?.lines.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
    ) {
        log?.let { current ->
            Text(
                text = stringResource(current.note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lines.isEmpty()) {
            Text(stringResource(SourceStrings.cgm_driver_log_empty))
        } else {
            Text(
                text = stringResource(SourceStrings.cgm_driver_log_count, lines.size),
                style = MaterialTheme.typography.labelMedium,
            )
            DexcomOnePlusLogList(lines)
        }
    }
}

/**
 * The lines, oldest first, opening at the newest one.
 *
 * Opening at the end: the state is made when the first lines arrive, with the last line as the
 * first one shown. The list then fills upward from it, so it never has to scroll to get there.
 *
 * Following: while the person is at the end, each new read scrolls to the end again. The log is
 * capped (`DriverLogReader.MAX_LINES`), so once it is full the number of lines no longer changes
 * and only the lines themselves do - which is why this watches the lines and not their count.
 * "Is the person at the end" is decided when a scroll stops, not when new lines arrive: by then
 * the new lines may already be measured, and a list that grew under the person would look like
 * one they had scrolled away from.
 */
@Composable
internal fun DexcomOnePlusLogList(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = lines.lastIndex.coerceAtLeast(0))
    var following by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        // The first value is the state before anything moved, not a decision by the person.
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .filter { !it }
            .collect { following = !listState.canScrollForward }
    }
    LaunchedEffect(lines) {
        if (following && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    SelectionContainer {
        LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
            items(lines) { line ->
                // Long lines wrap rather than run off the side: a driver line can be a
                // whole hex frame, and a phone is narrow.
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
