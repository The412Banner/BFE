package com.the412banner.bfe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.delay

/**
 * Pull down to refresh around a scrollable [content] (a list, a grid, a scrollable column, or one of
 * the scrollable empty/error messages). Releasing past the threshold calls [onRefresh] once; the
 * spinner stays a moment so the pull is seen to have worked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefreshBox(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val refresh by rememberUpdatedState(onRefresh)
    if (state.isRefreshing) {
        LaunchedEffect(true) {
            refresh()
            delay(400)
            state.endRefresh()
        }
    }
    Box(modifier.nestedScroll(state.nestedScrollConnection)) {
        content()
        // material3 1.2.0's PullToRefreshContainer draws its indicator even at rest;
        // only show it while the user is pulling or a refresh is running.
        if (state.verticalOffset > 0.5f || state.isRefreshing) {
            PullToRefreshContainer(
                state = state,
                containerColor = containerColor ?: PullToRefreshDefaults.containerColor,
                contentColor = contentColor ?: PullToRefreshDefaults.contentColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
