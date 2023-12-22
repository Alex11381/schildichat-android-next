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

package io.element.android.libraries.matrix.api.room

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

/**
 * Try to find an existing DM with the given user, or create one if none exists.
 */
suspend fun MatrixClient.startDM(userId: UserId): StartDMResult {
    val existingDM = findDM(userId)
    return if (existingDM != null) {
        StartDMResult.Success(existingDM, isNew = false)
    } else {
        createDM(userId).fold(
            { StartDMResult.Success(it, isNew = true) },
            { StartDMResult.Failure(it) }
        )
    }
}

sealed interface StartDMResult {
    data class Success(val roomId: RoomId, val isNew: Boolean) : StartDMResult
    data class Failure(val throwable: Throwable) : StartDMResult
}
