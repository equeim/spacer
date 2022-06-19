package org.equeim.spacer.ui.screen.donki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemsIndexed
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.olshevski.navigation.reimagined.navigate
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.LocalNavController
import org.equeim.spacer.R
import org.equeim.spacer.ui.components.RootScreenTopAppBar
import org.equeim.spacer.ui.screen.Destination
import org.equeim.spacer.ui.screen.settings.SettingsScreen
import org.equeim.spacer.ui.theme.Dimens
import org.equeim.spacer.ui.utils.addBottomInsetUnless
import org.equeim.spacer.ui.utils.hasBottomPadding
import org.equeim.spacer.ui.utils.toAppBarElevation

@Parcelize
object DonkiEventsScreen : Destination {
    @Composable
    override fun Content() = DonkiEventsScreen()
}

@Composable
private fun DonkiEventsScreen() {
    val model = viewModel<DonkiEventsScreenViewModel>()
    val items = model.pagingData.collectAsLazyPagingItems()
    DonkiEventsScreen(items)
}

@Composable
private fun DonkiEventsScreen(paging: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>) {
    val initialLazyListState = rememberLazyListState()
    val actualLazyListState = rememberLazyListState()
    val lazyListState = if (paging.itemCount == 0) initialLazyListState else actualLazyListState

    val scaffoldState = rememberScaffoldState()
    val snackbarError =
        paging.loadState.run { prepend as? LoadState.Error ?: append as? LoadState.Error }
    if (snackbarError != null) {
        val context = LocalContext.current
        LaunchedEffect(scaffoldState.snackbarHostState) {
            val result = scaffoldState.snackbarHostState.showSnackbar(
                message = context.getString(R.string.error),
                actionLabel = context.getString(R.string.retry),
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                paging.retry()
            }
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            RootScreenTopAppBar(
                stringResource(R.string.space_weather_events),
                elevation = lazyListState.toAppBarElevation()
            ) {
                val navController = LocalNavController.current
                IconButton(onClick = { navController.navigate(SettingsScreen) }) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                }
            }
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            val isInitialLoading = paging.loadState.refresh is LoadState.Loading ||
                    (paging.itemCount == 0 && paging.loadState.run {
                        append is LoadState.Loading || prepend is LoadState.Loading
                    })
            val swipeRefreshState = rememberSwipeRefreshState(isInitialLoading)
            SwipeRefresh(
                swipeRefreshState,
                onRefresh = { paging.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    paging.loadState.refresh is LoadState.Error -> DonkiEventsScreenContentErrorPlaceholder()
                    paging.itemCount == 0 -> DonkiEventsScreenContentLoadingPlaceholder()
                    else -> DonkiEventsScreenContentPaging(
                        lazyListState,
                        paging,
                        contentPadding.hasBottomPadding
                    )
                }
            }
            if (paging.itemCount != 0) {
                if (paging.loadState.prepend is LoadState.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopStart))
                }
                if (paging.loadState.append is LoadState.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomStart))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DonkiEventsScreenContentLoadingPlaceholder() {
    Text(
        text = stringResource(R.string.loading),
        style = MaterialTheme.typography.h6,
        modifier = Modifier.align(Alignment.Center)
    )
}

@Composable
private fun DonkiEventsScreenContentErrorPlaceholder() {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.error),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.h6
        )
    }
}

@Composable
private fun DonkiEventsScreenContentPaging(
    lazyListState: LazyListState,
    paging: LazyPagingItems<DonkiEventsScreenViewModel.ListItem>,
    screenHasBottomPadding: Boolean
) {
    val listPadding = PaddingValues(Dimens.ScreenContentPadding).addBottomInsetUnless(screenHasBottomPadding)
    LazyColumn(
        state = lazyListState,
        contentPadding = listPadding,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            paging,
            key = { _, item ->
                when (item) {
                    is DonkiEventsScreenViewModel.EventPresentation -> item.id
                    is DonkiEventsScreenViewModel.DateSeparator -> item.nextEventEpochSecond
                }
            }
        ) { index, item ->
            val isFirst = index == 0
            val isLast = index == paging.itemCount - 1
            checkNotNull(item)
            when (item) {
                is DonkiEventsScreenViewModel.DateSeparator -> {
                    Row(modifier = Modifier
                        .run { if (isFirst) padding(bottom = 8.dp) else padding(vertical = 8.dp) }
                        .fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colors.secondary,
                            elevation = 6.dp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            item.date
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                is DonkiEventsScreenViewModel.EventPresentation -> {
                    @OptIn(ExperimentalMaterialApi::class)
                    Card(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        onClick = {},
                        modifier = Modifier
                            .run { if (isLast) padding(top = 8.dp) else padding(vertical = 8.dp) }
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                        ) {
                            Text(text = item.time)
                            Text(
                                text = item.type,
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
