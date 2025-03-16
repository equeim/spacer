// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.notifications.details

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHostEntry
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.pop
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screens.Destination
import org.equeim.spacer.ui.screens.donki.LinkedEventPresentation
import org.equeim.spacer.ui.screens.donki.LinkedEventsList
import org.equeim.spacer.ui.screens.donki.events.DonkiEventsScreenViewModel.Companion.displayStringResId
import org.equeim.spacer.ui.screens.donki.events.details.DonkiEventDetailsScreen
import org.equeim.spacer.ui.screens.donki.notifications.details.NotificationDetailsScreenViewModel.ContentState
import org.equeim.spacer.ui.screens.donki.notifications.details.NotificationDetailsScreenViewModel.ContentState.Empty
import org.equeim.spacer.ui.screens.donki.notifications.details.NotificationDetailsScreenViewModel.ContentState.NotificationData
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.Public
import org.equeim.spacer.ui.theme.ScreenPreview
import org.equeim.spacer.ui.utils.createEventDateTimeFormatter
import org.equeim.spacer.utils.safeOpenUri
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@Parcelize
data class NotificationDetailsScreen(val notificationId: NotificationId) : Destination {
    @Composable
    override fun Content(
        navController: NavController<Destination>,
        navHostEntries: List<NavHostEntry<Destination>>,
        parentNavHostEntries: List<NavHostEntry<Destination>>?
    ) {
        val model = viewModel {
            NotificationDetailsScreenViewModel(
                notificationId,
                checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
            )
        }
        val state: ContentState by model.contentState.collectAsStateWithLifecycle()
        ScreenContent(
            state = state,
            popBackStack = navController::pop,
            showEventDetailsScreen = {
                navController.navigate(DonkiEventDetailsScreen(it))
            }
        )
    }
}

@Composable
private fun ScreenContent(
    state: ContentState,
    popBackStack: () -> Unit,
    showEventDetailsScreen: (EventId) -> Unit,
) {
    Scaffold(
        topBar = {
            SubScreenTopAppBar(
                stringResource(R.string.notification_details),
                popBackStack
            )
        },
        floatingActionButton = {
            val linkOrNull: String? by remember(state) {
                derivedStateOf {
                    (state as? NotificationData)?.link
                }
            }
            linkOrNull?.let { link ->
                val uriHandler = LocalUriHandler.current
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.go_to_donki_website)) },
                    icon = {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = stringResource(R.string.go_to_donki_website)
                        )
                    },
                    onClick = { uriHandler.safeOpenUri(link) }
                )
            }
        }
    ) { contentPadding ->
        Crossfade(state, label = "Content state crossfade") { state ->
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .padding(Dimens.ScreenContentPadding())
            ) {
                when (state) {
                    is Empty -> Unit
                    is ContentState.ErrorPlaceholder -> ScreenContentErrorPlaceholder(state.error)
                    is NotificationData -> ScreenContentNotificationData(
                        state,
                        showEventDetailsScreen
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScreenContentErrorPlaceholder(error: String) {
    Text(
        text = error,
        modifier = Modifier.align(Alignment.Center),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun ScreenContentNotificationData(
    notification: NotificationData,
    showEventDetailsScreen: (EventId) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.FloatingActionButtonPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Text(
            notification.title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(
                notification.dateTime,
                style = MaterialTheme.typography.titleMedium
            )
        }
        SelectionContainer {
            Text(notification.body.annotateLinks())
        }
        if (notification.linkedEvents.isNotEmpty()) {
            LinkedEventsList(notification.linkedEvents, showEventDetailsScreen)
        }
    }
}

@Composable
private fun NotificationDetailsScreenViewModel.BodyWithLinks.annotateLinks(): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val colored = remember(this, linkColor) {
        buildAnnotatedString {
            append(body)
            if (links.isNotEmpty()) {
                val styles = TextLinkStyles(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
                for ((link, range) in links) {
                    addLink(
                        LinkAnnotation.Url(link, styles),
                        range.first,
                        range.last + 1
                    )
                }
            }
        }
    }
    return colored
}

@Preview
@Composable
fun NotificationDetailsScreenPreview() {
    val formatter = createEventDateTimeFormatter(Locale.getDefault(), ZoneId.systemDefault())
    val body = """
        ## Community Coordinated Modeling Center Database Of Notifications, Knowledge, Information ( CCMC DONKI )
        ## Message Type: Space Weather Notification - CME update (Lucy, Solar Orbiter, STEREO A, Missions Near Earth)
        ##
        ## Message Issue Date: 2024-10-02T13:17:33Z
        ## Message ID: 20241002-AL-002
        ##
        ## Disclaimer: NOAA's Space Weather Prediction Center is the United States Government official source for space weather forecasts. This "Experimental Research Information" consists of preliminary NASA research products and should be interpreted and used accordingly.


        ## Summary:

        Update on CME with ID 2024-10-01T23:09:00-CME-001 (see previous notification 20241002-AL-001). Based on preliminary analysis by the Moon to Mars Space Weather Analysis Office and heliospheric modeling carried out at NASA Community Coordinated Modeling Center, it is estimated that this CME may affect Lucy, Solar Orbiter (glancing blow), and STEREO A (glancing blow).  The leading edge or flank of the CME may reach Lucy at 2024-10-04T19:43Z, Solar Orbiter at 2024-10-03T00:00Z, and STEREO A at 2024-10-04T16:30Z (plus minus 7 hours). 

        The simulation also indicates that the CME may impact NASA missions near Earth. Simulations indicate that the leading edge of the CME will reach NASA missions near Earth at about 2024-10-04T20:40Z (plus minus 7 hours). The roughly estimated expected range of the maximum Kp index is 4-6 (below minor to moderate).
           

        Updated CME parameters are (event upgraded to C-type):

        Start time of the event: 2024-10-01T23:09Z.

        Estimated speed: ~594 km/s.

        Estimated opening half-angle: 38 deg.

        Direction (lon./lat.): -19/-10 in Heliocentric Earth Equatorial coordinates.

        Activity ID: 2024-10-01T23:09:00-CME-001


        Links to the movies of the modeled event (includes CME: 2024-10-01T23:09:00-CME-001):

        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-den.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-vel.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-den-Stereo_A.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_anim.tim-vel-Stereo_A.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_timeline.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_STA_timeline.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_Lucy_timeline.gif
        http://iswa.gsfc.nasa.gov/downloads/20241002_042200_2.0_ENLIL_CONE_SolO_timeline.gif


        ## Notes: 

        This CME event (2024-10-01T23:09:00-CME-001) is associated with X7.1 flare with ID 2024-10-01T21:58:00-FLR-001 from Active Region 13942 (S17E18) which peaked at 2024-10-01T22:20Z (see notifications 20241001-AL-003 and 20241001-AL-004).


        SCORE CME typification system:
        S-type: CMEs with speeds less than 500 km/s
        C-type: Common 500-999 km/s
        O-type: Occasional 1000-1999 km/s
        R-type: Rare 2000-2999 km/s
        ER-type: Extremely Rare >3000 km/s
    """.trimIndent()
    ScreenPreview {
        ScreenContent(
            state = NotificationData(
                title = "CME update (Lucy, Solar Orbiter, STEREO A, Missions Near Earth)",
                dateTime = formatter.format(ZonedDateTime.now()),
                body = NotificationDetailsScreenViewModel.BodyWithLinks(body, emptyList()),
                link = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/view/Alert/33676/1",
                linkedEvents = listOf(
                    LinkedEventPresentation(
                        id = EventId("2024-10-01T23:09:00-CME-001"),
                        dateTime = formatter.format(ZonedDateTime.now()),
                        type = stringResource(EventType.CoronalMassEjection.displayStringResId)
                    )
                )
            ),
            popBackStack = {},
            showEventDetailsScreen = {}
        )
    }
}
