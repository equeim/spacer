package org.equeim.spacer.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.lifecycle.viewmodel.compose.viewModel
import org.equeim.spacer.R

@Composable
fun DonkiEventsScreen() {
    val model = viewModel<DonkiEventsScreenViewModel>()
    DonkiEventsScreen(model.uiState)
}

@Composable
private fun DonkiEventsScreen(state: DonkiEventsScreenViewModel.UiState) {
    val lazyListState = rememberLazyListState()
    Scaffold(topBar = {
        val appBarElevation = if (lazyListState.firstVisibleItemIndex == 0) {
            val itemSize = lazyListState.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.size
            if (itemSize != null) {
                val firstVisibleItemOffsetRelative =
                    lazyListState.firstVisibleItemScrollOffset.toFloat() / itemSize.toFloat()
                (firstVisibleItemOffsetRelative * AppBarDefaults.TopAppBarElevation.value).dp
            } else {
                0.dp
            }
        } else {
            AppBarDefaults.TopAppBarElevation
        }

        val appBarPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
        TopAppBar(
            backgroundColor = MaterialTheme.colors.surface,
            elevation = appBarElevation,
            contentPadding = PaddingValues(top = appBarPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.high,
                ) {
                    Text(
                        text = stringResource(R.string.space_weather_events),
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }) { contentPadding ->
        when (state) {
            is DonkiEventsScreenViewModel.UiState.Loading -> ScreenContentLoading(contentPadding)
            is DonkiEventsScreenViewModel.UiState.Error -> ScreenContentError(contentPadding)
            is DonkiEventsScreenViewModel.UiState.Loaded -> {
                ScreenContentLoaded(contentPadding, lazyListState, state)
            }
        }
    }
}

@Composable
private fun ScreenContentLoading(contentPadding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ScreenContentError(contentPadding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Text(
            text = stringResource(R.string.error),
            style = MaterialTheme.typography.h6,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun ScreenContentLoaded(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    state: DonkiEventsScreenViewModel.UiState.Loaded
) {
    val adjustedPadding = run {
        val addHorizontalPadding = 16.dp
        val layoutDirection = LocalLayoutDirection.current
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + addHorizontalPadding,
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection) + addHorizontalPadding,
            bottom = contentPadding.calculateBottomPadding().takeIf { it.value != 0.0f }
                ?: WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    }
    LazyColumn(
        state = lazyListState,
        contentPadding = adjustedPadding,
        modifier = Modifier.fillMaxSize()
    ) {
        item(contentType = null) {
            Text(
                text = stringResource(R.string.last_week),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
        state.eventGroups.forEach { group ->
            @OptIn(ExperimentalFoundationApi::class)
            stickyHeader(
                key = group.date,
                contentType = DonkiEventsScreenViewModel.EventsGroup::class
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colors.secondary, elevation = 6.dp) {
                        Text(
                            text = group.date,
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).align(Alignment.Top)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1.0f))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 6.dp,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = state.timeZoneName,
                            style = MaterialTheme.typography.subtitle1,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            )
                        )
                    }
                }
            }
            items(
                group.events,
                key = DonkiEventsScreenViewModel.EventPresentation::id,
                contentType = { DonkiEventsScreenViewModel.EventPresentation::class }) { event ->
                @OptIn(ExperimentalMaterialApi::class)
                Card(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(10.dp),
                    onClick = {},
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(text = event.time)
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewDonkiEventsScreen() {
    DonkiEventsScreen(
        DonkiEventsScreenViewModel.UiState.Loaded(
            listOf(
                DonkiEventsScreenViewModel.EventsGroup(
                    "This is date", listOf(
                        DonkiEventsScreenViewModel.EventPresentation("", "Solar flare", "22:44")
                    )
                )
            ),
            "UTC"
        )
    )
}
