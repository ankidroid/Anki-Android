/*
 *  Copyright (c) 2026 Tim Rae <perceptualchaos2@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.compose

import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.navigation.AppDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * App-level modal navigation drawer used by the new study screen.
 *
 * Items come from [AppDestination] grouped by [AppDestination.Group]. The drawer
 * owns its open/closed state — the host opens it by emitting on [openRequests];
 * dismissal (scrim tap, predictive back, item click) is handled internally.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AppNavigationDrawer(
    openRequests: Flow<Unit>,
    selected: AppDestination?,
    onDestinationClick: (AppDestination) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val headerDrawableId =
        TypedValue().let { value ->
            context.theme.resolveAttribute(R.attr.navDrawerImage, value, true)
            value.resourceId
        }

    LaunchedEffect(openRequests) {
        openRequests.collect { drawerState.open() }
    }

    // Skip emitting any UI while fully closed so touch events fall through to
    // the underlying View hierarchy.
    if (drawerState.currentValue == DrawerValue.Closed &&
        drawerState.targetValue == DrawerValue.Closed
    ) {
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                if (headerDrawableId != 0) {
                    Image(
                        painter = painterResource(headerDrawableId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                    )
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AppDestination.Group.entries.forEachIndexed { groupIndex, group ->
                        if (groupIndex > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        AppDestination.entries
                            .filter { it.group == group }
                            .forEach { dest ->
                                NavigationDrawerItem(
                                    selected = dest == selected,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        onDestinationClick(dest)
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(dest.iconRes),
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(stringResource(dest.titleRes)) },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                    }
                }
            }
        },
        content = {},
    )
}
