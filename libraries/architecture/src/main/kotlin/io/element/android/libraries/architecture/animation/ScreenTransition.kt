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

package io.element.android.libraries.architecture.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.scPrefs
import com.bumble.appyx.core.navigation.transition.ModifierTransitionHandler
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.transitionhandler.rememberBackstackSlider

@Composable
fun <NavTarget> rememberDefaultTransitionHandler(): ModifierTransitionHandler<NavTarget, BackStack.State> {
    val fastTransitions = scPrefs().settingState(scPref = ScPrefs.FAST_TRANSITIONS).value
    // "remember()" will not re-compose on settings change, so remember both values
    val upstreamSlider: ModifierTransitionHandler<NavTarget, BackStack.State> = rememberBackstackSlider(
        transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) },
    )
    val fastSlider: ModifierTransitionHandler<NavTarget, BackStack.State> = rememberBackstackSlider(
        transitionSpec = { spring(stiffness = Spring.StiffnessHigh) },
    )
    return if (fastTransitions) fastSlider else upstreamSlider
}
