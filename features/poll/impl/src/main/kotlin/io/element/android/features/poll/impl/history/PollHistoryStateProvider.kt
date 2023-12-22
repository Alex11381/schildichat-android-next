/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.poll.impl.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.poll.api.pollcontent.PollContentState
import io.element.android.features.poll.api.pollcontent.aPollContentState
import io.element.android.features.poll.impl.history.model.PollHistoryFilter
import io.element.android.features.poll.impl.history.model.PollHistoryItem
import io.element.android.features.poll.impl.history.model.PollHistoryItems
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

class PollHistoryStateProvider : PreviewParameterProvider<PollHistoryState> {
    override val values: Sequence<PollHistoryState>
        get() = sequenceOf(
            aPollHistoryState(
                isLoading = false,
                hasMoreToLoad = false,
                activeFilter = PollHistoryFilter.ONGOING,
            ),
            aPollHistoryState(
                isLoading = true,
                hasMoreToLoad = true,
                activeFilter = PollHistoryFilter.PAST,
            ),
        )
}

private fun aPollHistoryState(
    isLoading: Boolean = false,
    hasMoreToLoad: Boolean = false,
    activeFilter: PollHistoryFilter = PollHistoryFilter.ONGOING,
    currentItems: ImmutableList<PollHistoryItem> = persistentListOf(
        aPollHistoryItem(),
    ),
) = PollHistoryState(
    isLoading = isLoading,
    hasMoreToLoad = hasMoreToLoad,
    activeFilter = activeFilter,
    pollHistoryItems = PollHistoryItems(
        ongoing = currentItems,
        past = currentItems,
    ),
    eventSink = {},
)

private fun aPollHistoryItem(
    formattedDate: String = "01/12/2023",
    state: PollContentState = aPollContentState(),
) = PollHistoryItem(
    formattedDate = formattedDate,
    state = state,
)
