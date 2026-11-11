package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.stringResource

/**
 * Bar for a running standard bolus: what is being delivered, a progress bar and Stop. It sits in the
 * same place and looks like [EatReminderBanner], so the rest of the overview stays usable while the
 * bolus is delivered. This replaces the modal dialog that used to cover every screen.
 *
 * An SMB is not the user's own action and has never had this: the pump FAB shows it.
 *
 * @see BolusProgressBannerRunningPreview
 */
@Composable
fun BolusProgressBanner(
    state: BolusProgressState?,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = state?.takeUnless { it.isSMB }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        shown?.let { BolusProgressBannerContent(it, onStop, onDismiss, modifier) }
    }
}

@Composable
internal fun BolusProgressBannerContent(
    state: BolusProgressState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = stringResource(state.status)
    OverviewBannerCard(
        icon = IcBolus,
        color = AapsTheme.elementColors.insulin,
        title = stringResource(CoreUiStrings.goingtodeliver, state.insulin),
        lines = when {
            // Stalled (client only): the progress stream stopped, so a present-tense "Delivering ..." line
            // and a frozen bar would read as live delivery.
            state.stalled -> listOf(
                stringResource(CoreUiStrings.clientcontrol_bolus_progress_stalled_title),
                stringResource(CoreUiStrings.clientcontrol_bolus_progress_stalled_body)
            )

            statusText.isNotEmpty() -> listOf(statusText)
            else -> emptyList()
        },
        // Null while stalled: no bar. Zero percent means nothing received yet - the bar then fills from 0.
        progress = if (state.stalled) null else state.percent.coerceIn(0, 100) / 100f,
        modifier = modifier,
        actions = when {
            // Dismiss only hides this local view and does NOT stop the pump, so it stays neutral, not error-red.
            state.stalled -> {
                {
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(CoreUiStrings.dismiss))
                    }
                }
            }

            state.percent < 100 -> {
                {
                    FilledTonalButton(
                        onClick = onStop,
                        enabled = !state.stopPressed && state.stopDeliveryEnabled,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(
                            if (state.stopPressed) stringResource(CoreUiStrings.stop_pressed)
                            else stringResource(CoreUiStrings.stop)
                        )
                    }
                }
            }

            else -> null
        }
    )
}
