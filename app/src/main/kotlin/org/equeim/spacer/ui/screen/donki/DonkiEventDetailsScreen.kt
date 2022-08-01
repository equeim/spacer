package org.equeim.spacer.ui.screen.donki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.LocalNavController
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.CoronalMassEjection
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.ui.components.Card
import org.equeim.spacer.ui.components.SubScreenTopAppBar
import org.equeim.spacer.ui.screen.Destination
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.theme.Public
import org.equeim.spacer.ui.utils.addBottomInsetUnless
import org.equeim.spacer.ui.utils.hasBottomPadding
import org.equeim.spacer.ui.utils.plus
import org.equeim.spacer.utils.getApplicationOrThrow
import java.time.Instant

@Parcelize
data class DonkiEventDetailsScreen(val eventId: EventId) : Destination {
    @Composable
    override fun Content() = ScreenContent(eventId)
}

@Composable
private fun ScreenContent(eventId: EventId) {
    val context = LocalContext.current
    @Suppress("UNCHECKED_CAST")
    val model =
        viewModel<DonkiEventDetailsScreenViewModel>(factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                DonkiEventDetailsScreenViewModel(eventId, context.getApplicationOrThrow()) as T
        })
    ScreenContent(model)
}

@Composable
private fun ScreenContent(
    model: DonkiEventDetailsScreenViewModel
) {
    Scaffold(topBar = { SubScreenTopAppBar(stringResource(R.string.event_details)) },
        floatingActionButton = {
            val contentUiState = model.contentUiState
            if (contentUiState is DonkiEventDetailsScreenViewModel.ContentUiState.Loaded) {
                val uriHandler = LocalUriHandler.current
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.go_to_donki_website)) },
                    icon = {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = stringResource(R.string.go_to_donki_website)
                        )
                    },
                    onClick = { uriHandler.openUri(contentUiState.event.link) },
                    modifier = Modifier.padding(
                        bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }) { contentPadding ->
        val showRefreshIndicator by remember(model) {
            derivedStateOf {
                model.contentUiState is DonkiEventDetailsScreenViewModel.ContentUiState.Loading ||
                        model.refreshing
            }
        }
        SwipeRefresh(
            rememberSwipeRefreshState(showRefreshIndicator),
            onRefresh = { model.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
            ) {
                when (val contentUiState = model.contentUiState) {
                    is DonkiEventDetailsScreenViewModel.ContentUiState.Loading -> ScreenContentLoadingPlaceholder()
                    is DonkiEventDetailsScreenViewModel.ContentUiState.Error -> ScreenContentErrorPlaceholder()
                    is DonkiEventDetailsScreenViewModel.ContentUiState.Loaded -> {
                        ScreenContentLoaded(contentUiState, contentPadding.hasBottomPadding, model::formatTime)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScreenContentLoadingPlaceholder() {
    Text(
        text = stringResource(R.string.loading),
        style = MaterialTheme.typography.h6,
        modifier = Modifier.align(Alignment.Center)
    )
}

@Composable
private fun BoxScope.ScreenContentErrorPlaceholder() {
    Text(
        text = stringResource(R.string.error),
        modifier = Modifier.align(Alignment.Center),
        style = MaterialTheme.typography.h6
    )
}

@Composable
private fun ScreenContentLoaded(
    state: DonkiEventDetailsScreenViewModel.ContentUiState.Loaded,
    screenHasBottomPadding: Boolean,
    formatTime: (Instant) -> String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                PaddingValues(Dimens.ScreenContentPadding)
                    .plus(
                        PaddingValues(
                            bottom = 64.dp
                        )
                    )
                    .addBottomInsetUnless(screenHasBottomPadding)
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            state.type,
            color = MaterialTheme.colors.primary,
            style = MaterialTheme.typography.h5
        )
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                state.dateTime,
                style = MaterialTheme.typography.h6
            )
        }
        SpecificEventDetails(state.event, formatTime)
        if (state.linkedEvents.isNotEmpty()) {
            SectionHeader(stringResource(R.string.linked_events))
            val navController = LocalNavController.current
            state.linkedEvents.forEach { linkedEvent ->
                Card(
                    { navController.navigate(DonkiEventDetailsScreen(linkedEvent.id)) },
                    Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(text = linkedEvent.dateTime)
                        Text(
                            text = linkedEvent.type,
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecificEventDetails(event: Event, formatTime: (Instant) -> String) {
    when (event) {
        is CoronalMassEjection -> CoronalMassEjectionDetails(event, formatTime)
        else -> Unit
    }
}

@Composable
fun SectionHeader(title: String, style: TextStyle = MaterialTheme.typography.h6) {
    Text(
        title,
        color = MaterialTheme.colors.primary,
        style = style,
        modifier = Modifier.padding(top = 8.dp)
    )
}
