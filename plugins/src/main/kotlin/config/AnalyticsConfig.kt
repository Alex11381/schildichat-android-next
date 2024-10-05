/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE in the repository root for full details.
 */

package config

sealed interface AnalyticsConfig {
    data class Enabled(
        val withPosthog: Boolean,
        val withSentry: Boolean,
    ) : AnalyticsConfig {
        init {
            require(withPosthog || withSentry) {
                "At least one analytics provider must be enabled"
            }
        }
    }

    object Disabled : AnalyticsConfig
}
