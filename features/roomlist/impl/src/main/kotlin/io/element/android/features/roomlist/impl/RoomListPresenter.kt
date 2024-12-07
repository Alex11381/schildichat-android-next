/*
 * Copyright 2023, 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE in the repository root for full details.
 */

package io.element.android.features.roomlist.impl

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import chat.schildi.features.roomlist.spaces.PersistSpaceOnPause
import chat.schildi.features.roomlist.spaces.SpaceAwareRoomListDataSource
import chat.schildi.features.roomlist.spaces.SpaceListDataSource
import chat.schildi.features.roomlist.spaces.SpaceUnreadCountsDataSource
import chat.schildi.features.roomlist.spaces.filterByUnread
import chat.schildi.features.roomlist.spaces.resolveSelection
import chat.schildi.lib.preferences.ScAppStateStore
import chat.schildi.lib.preferences.ScPreferencesStore
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.features.invite.api.response.AcceptDeclineInviteEvents
import io.element.android.features.invite.api.response.AcceptDeclineInviteState
import io.element.android.features.invite.api.response.InviteData
import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.features.logout.api.direct.DirectLogoutState
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.features.roomlist.impl.datasource.RoomListDataSource
import io.element.android.features.roomlist.impl.filters.RoomListFiltersState
import io.element.android.features.roomlist.impl.model.RoomListRoomSummary
import io.element.android.features.roomlist.impl.model.hasNewContent
import io.element.android.features.roomlist.impl.search.RoomListSearchEvents
import io.element.android.features.roomlist.impl.search.RoomListSearchState
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.sync.SlidingSyncVersion
import io.element.android.libraries.matrix.api.timeline.ReceiptType
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.push.api.notifications.NotificationCleaner
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val EXTENDED_RANGE_SIZE = 40
private const val SUBSCRIBE_TO_VISIBLE_ROOMS_DEBOUNCE_IN_MILLIS = 300L

class RoomListPresenter @Inject constructor(
    private val client: MatrixClient,
    private val networkMonitor: NetworkMonitor,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val leaveRoomPresenter: Presenter<LeaveRoomState>,
    private val roomListDataSource: RoomListDataSource,
    private val scPreferencesStore: ScPreferencesStore,
    private val scAppStateStore: ScAppStateStore,
    private val spaceAwareRoomListDataSource: SpaceAwareRoomListDataSource,
    private val spaceListDataSource: SpaceListDataSource,
    private val spaceUnreadCountsDataSource: SpaceUnreadCountsDataSource,
    private val featureFlagService: FeatureFlagService,
    private val indicatorService: IndicatorService,
    private val filtersPresenter: Presenter<RoomListFiltersState>,
    private val searchPresenter: Presenter<RoomListSearchState>,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val analyticsService: AnalyticsService,
    private val acceptDeclineInvitePresenter: Presenter<AcceptDeclineInviteState>,
    private val fullScreenIntentPermissionsPresenter: Presenter<FullScreenIntentPermissionsState>,
    private val notificationCleaner: NotificationCleaner,
    private val logoutPresenter: Presenter<DirectLogoutState>,
) : Presenter<RoomListState> {
    private val encryptionService: EncryptionService = client.encryptionService()

    @Composable
    override fun present(): RoomListState {
        val coroutineScope = rememberCoroutineScope()
        val leaveRoomState = leaveRoomPresenter.present()
        val matrixUser = client.userProfile.collectAsState()
        val networkConnectionStatus by networkMonitor.connectivity.collectAsState()
        val filtersState = filtersPresenter.present()
        val searchState = searchPresenter.present()
        val acceptDeclineInviteState = acceptDeclineInvitePresenter.present()

        LaunchedEffect(Unit) {
            spaceListDataSource.launchIn(this)
            roomListDataSource.launchIn(this)
            spaceAwareRoomListDataSource.launchIn(this, roomListDataSource, spaceListDataSource, scAppStateStore)
            spaceUnreadCountsDataSource.launchIn(this, roomListDataSource, spaceAwareRoomListDataSource, spaceListDataSource)
            // Force a refresh of the profile
            client.getUserProfile()
        }
        PersistSpaceOnPause(scAppStateStore, spaceAwareRoomListDataSource)

        var securityBannerDismissed by rememberSaveable { mutableStateOf(false) }

        // Avatar indicator
        val showAvatarIndicator by indicatorService.showRoomListTopBarIndicator()

        val contextMenu = remember { mutableStateOf<RoomListState.ContextMenu>(RoomListState.ContextMenu.Hidden) }


        val spaceNavEnabled = ScPrefs.SPACE_NAV.value()

        val directLogoutState = logoutPresenter.present()

        fun handleEvents(event: RoomListEvents) {
            when (event) {
                is RoomListEvents.UpdateSpaceFilter -> { spaceAwareRoomListDataSource.updateSpaceSelection(event.spaceSelectionHierarchy.toImmutableList()) }
                is RoomListEvents.UpdateVisibleRange -> coroutineScope.launch {
                    updateVisibleRange(event.range, spaceNavEnabled)
                }
                RoomListEvents.DismissRequestVerificationPrompt -> securityBannerDismissed = true
                RoomListEvents.DismissBanner -> securityBannerDismissed = true
                RoomListEvents.ToggleSearchResults -> searchState.eventSink(RoomListSearchEvents.ToggleSearchVisibility)
                is RoomListEvents.ShowContextMenu -> {
                    coroutineScope.showContextMenu(event, contextMenu)
                }
                is RoomListEvents.HideContextMenu -> {
                    contextMenu.value = RoomListState.ContextMenu.Hidden
                }
                is RoomListEvents.LeaveRoom -> leaveRoomState.eventSink(LeaveRoomEvent.ShowConfirmation(event.roomId))
                is RoomListEvents.SetRoomIsFavorite -> coroutineScope.setRoomIsFavorite(event.roomId, event.isFavorite)
                is RoomListEvents.MarkAsRead -> coroutineScope.markAsRead(event.roomId)
                is RoomListEvents.MarkAsUnread -> coroutineScope.markAsUnread(event.roomId)
                is RoomListEvents.AcceptInvite -> {
                    acceptDeclineInviteState.eventSink(
                        AcceptDeclineInviteEvents.AcceptInvite(event.roomListRoomSummary.toInviteData())
                    )
                }
                is RoomListEvents.DeclineInvite -> {
                    acceptDeclineInviteState.eventSink(
                        AcceptDeclineInviteEvents.DeclineInvite(event.roomListRoomSummary.toInviteData())
                    )
                }
            }
        }

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()

        val contentState = roomListContentState(securityBannerDismissed)

        return RoomListState(
            matrixUser = matrixUser.value,
            showAvatarIndicator = showAvatarIndicator,
            snackbarMessage = snackbarMessage,
            hasNetworkConnection = networkConnectionStatus == NetworkStatus.Online,
            contextMenu = contextMenu.value,
            leaveRoomState = leaveRoomState,
            filtersState = filtersState,
            searchState = searchState,
            contentState = contentState,
            acceptDeclineInviteState = acceptDeclineInviteState,
            directLogoutState = directLogoutState,
            eventSink = ::handleEvents,
        )
    }

    @Composable
    private fun rememberSecurityBannerState(
        securityBannerDismissed: Boolean,
        needsSlidingSyncMigration: Boolean,
    ): State<SecurityBannerState> {
        val currentSecurityBannerDismissed by rememberUpdatedState(securityBannerDismissed)
        val currentNeedsSlidingSyncMigration by rememberUpdatedState(needsSlidingSyncMigration)
        val recoveryState by encryptionService.recoveryStateStateFlow.collectAsState()
        return remember {
            derivedStateOf {
                calculateBannerState(
                    securityBannerDismissed = currentSecurityBannerDismissed,
                    needsSlidingSyncMigration = currentNeedsSlidingSyncMigration,
                    recoveryState = recoveryState,
                )
            }
        }
    }

    private fun calculateBannerState(
        securityBannerDismissed: Boolean,
        needsSlidingSyncMigration: Boolean,
        recoveryState: RecoveryState,
    ): SecurityBannerState {
        if (securityBannerDismissed) {
            return SecurityBannerState.None
        }

        when (recoveryState) {
            RecoveryState.DISABLED -> return SecurityBannerState.SetUpRecovery
            RecoveryState.INCOMPLETE -> return SecurityBannerState.RecoveryKeyConfirmation
            RecoveryState.UNKNOWN,
            RecoveryState.WAITING_FOR_SYNC,
            RecoveryState.ENABLED -> Unit
        }

        if (needsSlidingSyncMigration) {
            return SecurityBannerState.NeedsNativeSlidingSyncMigration
        }

        return SecurityBannerState.None
    }

    @Composable
    private fun roomListContentState(
        securityBannerDismissed: Boolean,
    ): RoomListContentState {
        // SC spaces
        val spaceNavEnabled = ScPrefs.SPACE_NAV.value()
        val spaceSelectionHierarchy = if (spaceNavEnabled) spaceAwareRoomListDataSource.spaceSelectionHierarchy.collectAsState().value else persistentListOf()
        val totalUnreadCounts = if (spaceNavEnabled) spaceUnreadCountsDataSource.totalUnreadCounts.collectAsState().value else null
        val spacesList = if (spaceNavEnabled) spaceUnreadCountsDataSource.enrichedSpaces.collectAsState().value?.filterByUnread(spaceSelectionHierarchy) else null
        // SC end

        val roomSummaries = if (spaceNavEnabled)
            produceState(initialValue = AsyncData.Loading()) { spaceAwareRoomListDataSource.spaceRooms.collect { value = AsyncData.Success(it) } }.value
        else
            produceState(initialValue = AsyncData.Loading()) { roomListDataSource.allRooms.collect { value = AsyncData.Success(it) } }.value
        val loadingState by roomListDataSource.loadingState.collectAsState()
        val showEmpty = //by remember {
            //derivedStateOf {
                (loadingState as? RoomList.LoadingState.Loaded)?.numberOfRooms == 0
            //}
        //}
        val showSkeleton = //by remember {
            //derivedStateOf {
                loadingState == RoomList.LoadingState.NotLoaded || roomSummaries is AsyncData.Loading
            //}
        //}
        val needsSlidingSyncMigration by produceState(false) {
            value = client.needsSlidingSyncMigration().getOrDefault(false)
        }
        val securityBannerState by rememberSecurityBannerState(securityBannerDismissed, needsSlidingSyncMigration)
        return when {
            showEmpty -> RoomListContentState.Empty(securityBannerState = securityBannerState)
            showSkeleton -> RoomListContentState.Skeleton(count = 16)
            else -> {
                RoomListContentState.Rooms(
                    // SC start
                    spacesList = spacesList.orEmpty().toImmutableList(),
                    spaceSelectionHierarchy = spaceSelectionHierarchy?.takeIf { spacesList?.resolveSelection(it) != null } ?: persistentListOf(),
                    totalUnreadCounts = totalUnreadCounts,
                    // SC end
                    securityBannerState = securityBannerState,
                    fullScreenIntentPermissionsState = fullScreenIntentPermissionsPresenter.present(),
                    summaries = roomSummaries.dataOrNull().orEmpty().toPersistentList()
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.showContextMenu(event: RoomListEvents.ShowContextMenu, contextMenuState: MutableState<RoomListState.ContextMenu>) = launch {
        val initialState = RoomListState.ContextMenu.Shown(
            roomId = event.roomListRoomSummary.roomId,
            roomName = event.roomListRoomSummary.name,
            isDm = event.roomListRoomSummary.isDm,
            isFavorite = event.roomListRoomSummary.isFavorite,
            markAsUnreadFeatureFlagEnabled = featureFlagService.isFeatureEnabled(FeatureFlags.MarkAsUnread),
            hasNewContent = event.roomListRoomSummary.hasNewContent(scPreferencesStore)
        )
        contextMenuState.value = initialState

        client.getRoom(event.roomListRoomSummary.roomId)?.use { room ->

            val isShowingContextMenuFlow = snapshotFlow { contextMenuState.value is RoomListState.ContextMenu.Shown }
                .distinctUntilChanged()

            val isFavoriteFlow = room.roomInfoFlow
                .map { it.isFavorite }
                .distinctUntilChanged()

            isFavoriteFlow
                .onEach { isFavorite ->
                    contextMenuState.value = initialState.copy(isFavorite = isFavorite)
                }
                .flatMapLatest { isShowingContextMenuFlow }
                .takeWhile { isShowingContextMenu -> isShowingContextMenu }
                .collect()
        }
    }

    private fun CoroutineScope.setRoomIsFavorite(roomId: RoomId, isFavorite: Boolean) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setIsFavorite(isFavorite)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuFavouriteToggle)
                }
        }
    }

    private fun CoroutineScope.markAsRead(roomId: RoomId) = launch {
        notificationCleaner.clearMessagesForRoom(client.sessionId, roomId)
        client.getRoom(roomId)?.use { room ->
            room.setUnreadFlag(isUnread = false)
            val receiptType = if (sessionPreferencesStore.isSendPublicReadReceiptsEnabled().first()) {
                ReceiptType.READ
            } else {
                ReceiptType.READ_PRIVATE
            }
            room.markAsRead(receiptType)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle)
                }
        }
    }

    private fun CoroutineScope.markAsUnread(roomId: RoomId) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setUnreadFlag(isUnread = true)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle)
                }
        }
    }

    /**
     * Checks if the user needs to migrate to a native sliding sync version.
     */
    private suspend fun MatrixClient.needsSlidingSyncMigration(): Result<Boolean> = runCatching {
        val currentSlidingSyncVersion = currentSlidingSyncVersion().getOrThrow()
        if (currentSlidingSyncVersion != SlidingSyncVersion.Native) {
            val availableSlidingSyncVersions = availableSlidingSyncVersions().getOrThrow()
            availableSlidingSyncVersions.contains(SlidingSyncVersion.Native)
        } else {
            false
        }
    }

    private var currentUpdateVisibleRangeJob: Job? = null
    private fun CoroutineScope.updateVisibleRange(range: IntRange, spaceNavEnabled: Boolean) {
        currentUpdateVisibleRangeJob?.cancel()
        currentUpdateVisibleRangeJob = launch {
            // Debounce the subscription to avoid subscribing to too many rooms
            delay(SUBSCRIBE_TO_VISIBLE_ROOMS_DEBOUNCE_IN_MILLIS)

            if (range.isEmpty()) return@launch
            val currentRoomList = if (spaceNavEnabled) spaceAwareRoomListDataSource.spaceRooms.first() else roomListDataSource.allRooms.first()
            // Use extended range to 'prefetch' the next rooms info
            val midExtendedRangeSize = EXTENDED_RANGE_SIZE / 2
            val extendedRange = range.first until range.last + midExtendedRangeSize
            val roomIds = extendedRange.mapNotNull { index ->
                currentRoomList.getOrNull(index)?.roomId
            }
            roomListDataSource.subscribeToVisibleRooms(roomIds)
        }
    }
}

@VisibleForTesting
internal fun RoomListRoomSummary.toInviteData() = InviteData(
    roomId = roomId,
    // Note: `name` should not be null at this point, but just in case, fallback to the roomId
    roomName = name ?: roomId.value,
    isDm = isDm,
)
