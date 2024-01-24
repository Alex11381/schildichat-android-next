package io.element.android.features.messages.impl

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chat.schildi.components.preferences.AutoRenderedDropdown
import chat.schildi.lib.R
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import io.element.android.features.messages.impl.timeline.TimelineEvents
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
internal fun RowScope.scMessagesViewTopBarActions(state: MessagesState) {
    if (ScPrefs.SC_DEV_QUICK_OPTIONS.value()) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            onClick = { showMenu = !showMenu }
        ) {
            Icon(
                resourceId = CompoundDrawables.ic_overflow_vertical,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (ScPrefs.SYNC_READ_RECEIPT_AND_MARKER.value()) {
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        state.timelineState.eventSink(TimelineEvents.MarkAsRead)
                    },
                    text = { Text(stringResource(id = R.string.sc_action_mark_as_read)) },
                )
            }
            ScPrefs.devQuickTweaksTimeline.forEach {
                it.AutoRenderedDropdown(
                    onClick = { showMenu = false }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}
