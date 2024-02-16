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

package io.element.android.features.roomlist.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import chat.schildi.features.roomlist.spaces.PersistSpaceOnPause
import chat.schildi.features.roomlist.spaces.SpaceAwareRoomListDataSource
import chat.schildi.features.roomlist.spaces.SpaceListDataSource
import chat.schildi.features.roomlist.spaces.SpaceUnreadCountsDataSource
import chat.schildi.lib.preferences.ScAppStateStore
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.features.leaveroom.api.LeaveRoomPresenter
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.features.preferences.api.store.SessionPreferencesStore
import io.element.android.features.roomlist.impl.datasource.InviteStateDataSource
import io.element.android.features.roomlist.impl.datasource.RoomListDataSource
import io.element.android.features.roomlist.impl.migration.MigrationScreenPresenter
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.timeline.ReceiptType
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.user.getCurrentUser
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val EXTENDED_RANGE_SIZE = 40

class RoomListPresenter @Inject constructor(
    private val client: MatrixClient,
    private val sessionVerificationService: SessionVerificationService,
    private val networkMonitor: NetworkMonitor,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val inviteStateDataSource: InviteStateDataSource,
    private val leaveRoomPresenter: LeaveRoomPresenter,
    private val roomListDataSource: RoomListDataSource,
    private val spaceAwareRoomListDataSource: SpaceAwareRoomListDataSource,
    private val spaceListDataSource: SpaceListDataSource,
    private val spaceUnreadCountsDataSource: SpaceUnreadCountsDataSource,
    private val encryptionService: EncryptionService,
    private val featureFlagService: FeatureFlagService,
    private val indicatorService: IndicatorService,
    private val scAppStateStore: ScAppStateStore,
    private val migrationScreenPresenter: MigrationScreenPresenter,
    private val sessionPreferencesStore: SessionPreferencesStore,
) : Presenter<RoomListState> {
    @Composable
    override fun present(): RoomListState {
        val coroutineScope = rememberCoroutineScope()
        val leaveRoomState = leaveRoomPresenter.present()
        val matrixUser: MutableState<MatrixUser?> = rememberSaveable {
            mutableStateOf(null)
        }
        val spaceNavEnabled = ScPrefs.SPACE_NAV.value()
        val spacesList = if (spaceNavEnabled) spaceListDataSource.allSpaces.collectAsState().value else null
        val spaceSelectionHierarchy = if (spaceNavEnabled) spaceAwareRoomListDataSource.spaceSelectionHierarchy.collectAsState().value else persistentListOf()
        val spaceUnreadCounts = if (spaceNavEnabled) spaceUnreadCountsDataSource.spaceUnreadCounts.collectAsState().value else persistentMapOf()
        val roomList by produceState(initialValue = AsyncData.Loading()) {
            if (spaceNavEnabled) {
                spaceAwareRoomListDataSource.spaceRooms.collect { value = AsyncData.Success(it) }
            } else {
                roomListDataSource.allRooms.collect { value = AsyncData.Success(it) }
            }
        }
        val filteredRoomList by roomListDataSource.filteredRooms.collectAsState()
        val filter by roomListDataSource.filter.collectAsState()
        val networkConnectionStatus by networkMonitor.connectivity.collectAsState()

        LaunchedEffect(Unit) {
            spaceListDataSource.launchIn(this)
            roomListDataSource.launchIn(this)
            spaceAwareRoomListDataSource.launchIn(this, roomListDataSource, spaceListDataSource, scAppStateStore)
            spaceUnreadCountsDataSource.launchIn(this, roomListDataSource, spaceAwareRoomListDataSource, spaceListDataSource)
            initialLoad(matrixUser)
        }
        PersistSpaceOnPause(scAppStateStore, spaceAwareRoomListDataSource)

        val isMigrating = migrationScreenPresenter.present().isMigrating

        // Session verification status (unknown, not verified, verified)
        val canVerifySession by sessionVerificationService.canVerifySessionFlow.collectAsState(initial = false)
        var verificationPromptDismissed by rememberSaveable { mutableStateOf(false) }
        // We combine both values to only display the prompt if the session is not verified and it wasn't dismissed
        val displayVerificationPrompt by remember {
            derivedStateOf { canVerifySession && !verificationPromptDismissed }
        }
        val recoveryState by encryptionService.recoveryStateStateFlow.collectAsState()
        val secureStorageFlag by featureFlagService.isFeatureEnabledFlow(FeatureFlags.SecureStorage)
            .collectAsState(initial = null)
        var recoveryKeyPromptDismissed by rememberSaveable { mutableStateOf(false) }
        val displayRecoveryKeyPrompt by remember {
            derivedStateOf {
                secureStorageFlag == true &&
                    recoveryState == RecoveryState.INCOMPLETE &&
                    !recoveryKeyPromptDismissed
            }
        }

        val markAsUnreadFeatureFlagEnabled by featureFlagService.isFeatureEnabledFlow(FeatureFlags.MarkAsUnread)
            .collectAsState(initial = null)

        // Avatar indicator
        val showAvatarIndicator by indicatorService.showRoomListTopBarIndicator()

        var displaySearchResults by rememberSaveable { mutableStateOf(false) }

        var contextMenu by remember { mutableStateOf<RoomListState.ContextMenu>(RoomListState.ContextMenu.Hidden) }

        fun handleEvents(event: RoomListEvents) {
            when (event) {
                is RoomListEvents.UpdateFilter -> roomListDataSource.updateFilter(event.newFilter)
                is RoomListEvents.UpdateSpaceFilter -> { spaceAwareRoomListDataSource.updateSpaceSelection(event.spaceSelectionHierarchy.toImmutableList()) }
                is RoomListEvents.UpdateVisibleRange -> updateVisibleRange(event.range, event.withSpaceFilter)
                RoomListEvents.DismissRequestVerificationPrompt -> verificationPromptDismissed = true
                RoomListEvents.DismissRecoveryKeyPrompt -> recoveryKeyPromptDismissed = true
                RoomListEvents.ToggleSearchResults -> {
                    if (displaySearchResults) {
                        roomListDataSource.updateFilter("")
                    }
                    displaySearchResults = !displaySearchResults
                }
                is RoomListEvents.ShowContextMenu -> {
                    contextMenu = RoomListState.ContextMenu.Shown(
                        roomId = event.roomListRoomSummary.roomId,
                        roomName = event.roomListRoomSummary.name,
                        isUnread = event.roomListRoomSummary.let { it.numberOfUnreadMessages > 0 || it.markedUnread },
                        isDm = event.roomListRoomSummary.isDm,
                        markAsUnreadFeatureFlagEnabled = markAsUnreadFeatureFlagEnabled == true,
                        hasNewContent = event.roomListRoomSummary.hasNewContent
                    )
                }
                is RoomListEvents.HideContextMenu -> contextMenu = RoomListState.ContextMenu.Hidden
                is RoomListEvents.LeaveRoom -> leaveRoomState.eventSink(LeaveRoomEvent.ShowConfirmation(event.roomId))
                is RoomListEvents.MarkAsRead -> coroutineScope.launch {
                    client.getRoom(event.roomId)?.use { room ->
                        room.setUnreadFlag(isUnread = false)
                        val receiptType = if (sessionPreferencesStore.isSendPublicReadReceiptsEnabled().first()) {
                            ReceiptType.READ
                        } else {
                            ReceiptType.READ_PRIVATE
                        }
                        room.markAsRead(receiptType)
                    }
                }
                is RoomListEvents.MarkAsUnread -> coroutineScope.launch {
                    client.getRoom(event.roomId)?.use { room ->
                        room.setUnreadFlag(isUnread = true)
                    }
                }
            }
        }

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()

        return RoomListState(
            matrixUser = matrixUser.value,
            showAvatarIndicator = showAvatarIndicator,
            roomList = roomList,
            spacesList = spacesList.orEmpty().toImmutableList(),
            spaceSelectionHierarchy = spaceSelectionHierarchy ?: persistentListOf(),
            spaceUnreadCounts = spaceUnreadCounts,
            filter = filter,
            filteredRoomList = filteredRoomList,
            displayVerificationPrompt = displayVerificationPrompt,
            displayRecoveryKeyPrompt = displayRecoveryKeyPrompt,
            snackbarMessage = snackbarMessage,
            hasNetworkConnection = networkConnectionStatus == NetworkStatus.Online,
            invitesState = inviteStateDataSource.inviteState(),
            displaySearchResults = displaySearchResults,
            contextMenu = contextMenu,
            leaveRoomState = leaveRoomState,
            displayMigrationStatus = isMigrating,
            eventSink = ::handleEvents
        )
    }

    private fun CoroutineScope.initialLoad(matrixUser: MutableState<MatrixUser?>) = launch {
        matrixUser.value = client.getCurrentUser()
    }

    private fun updateVisibleRange(range: IntRange, withSpaceFilter: Boolean) {
        if (range.isEmpty()) return
        val midExtendedRangeSize = EXTENDED_RANGE_SIZE / 2
        val extendedRangeStart = (range.first - midExtendedRangeSize).coerceAtLeast(0)
        // Safe to give bigger size than room list
        val extendedRangeEnd = range.last + midExtendedRangeSize
        val extendedRange = IntRange(extendedRangeStart, extendedRangeEnd)
        client.roomListService.updateAllRoomsVisibleRange(extendedRange, withSpaceFilter)
    }
}
