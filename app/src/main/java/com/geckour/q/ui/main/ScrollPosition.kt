package com.geckour.q.ui.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val RESTORE_SCROLL_TIMEOUT = 5.seconds

private const val KEY_SCROLL_POSITION = "scrollPosition"

/**
 * Scroll position of the list shown by a destination.
 *
 * The position is held in the [SavedStateHandle] of the [NavBackStackEntry] of the destination, so
 * that it lives exactly as long as the destination stays in the back stack: it is kept while the
 * destination is covered by another one, it is saved and restored along with the back stack on a
 * configuration change or process death, and it is discarded once the destination is popped.
 */
@Stable
class ScrollPosition internal constructor(private val savedStateHandle: SavedStateHandle) {

    /** The position to restore on entering the destination. */
    val initialPosition: Pair<Int, Int> =
        savedStateHandle.get<IntArray>(KEY_SCROLL_POSITION)
            ?.let { it[0] to it[1] }
            ?: (0 to 0)

    /**
     * Publishes the latest position. It is intentionally not a state: it is read only on entering
     * the destination, and writing it must not cause a recomposition.
     */
    fun update(index: Int, offset: Int) {
        savedStateHandle[KEY_SCROLL_POSITION] = intArrayOf(index, offset)
    }
}

/**
 * Remembers the [ScrollPosition] of [backStackEntry].
 *
 * The [SavedStateHandle] is resolved once on entering the destination, so that publishing a
 * position never touches the entry after it has been destroyed.
 */
@Composable
fun rememberScrollPosition(backStackEntry: NavBackStackEntry): ScrollPosition =
    remember(backStackEntry) { ScrollPosition(backStackEntry.savedStateHandle) }

/**
 * Restores the scroll position of [listState] to [initialScrollPosition] and keeps the latest
 * position published through [onScrollPositionUpdated].
 *
 * The contents of these lists are loaded asynchronously (paged from the DB), so restoring cannot
 * be done right away: measuring the still empty list would clamp the position back to the top.
 * Therefore the restoration waits until the item the position points at is loaded, and no position
 * is published until it has been done, so that the position to restore is not overwritten by the
 * one of the empty list.
 *
 * @param headerItemCount count of the items placed before the loaded items in the list
 * @param isItemLoaded whether the item of the given index (excluding the header items) is loaded
 */
@Composable
fun ScrollPositionEffect(
    listState: LazyListState,
    initialScrollPosition: Pair<Int, Int>,
    headerItemCount: Int,
    isItemLoaded: (itemIndex: Int) -> Boolean,
    onScrollPositionUpdated: (newIndex: Int, newOffset: Int) -> Unit,
) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (index, offset) = initialScrollPosition
        if (index > 0 || offset > 0) {
            val itemIndex = index - headerItemCount
            if (itemIndex >= 0) {
                withTimeoutOrNull(RESTORE_SCROLL_TIMEOUT) {
                    snapshotFlow { isItemLoaded(itemIndex) }.first { it }
                }
            }
            listState.scrollToItem(index = index, scrollOffset = offset)
        }
        restored = true
    }

    LaunchedEffect(restored, listState.isScrollInProgress) {
        if (restored && listState.isScrollInProgress.not()) {
            onScrollPositionUpdated(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }
}

/**
 * Scrolls [listState] to the top every time [scrollToTop] is updated.
 *
 * The value it holds on the first composition is ignored, so that entering a destination again
 * does not discard the scroll position restored by [ScrollPositionEffect].
 */
@Composable
fun ScrollToTopEffect(listState: LazyListState, scrollToTop: Long) {
    val initialScrollToTop = remember { scrollToTop }

    LaunchedEffect(scrollToTop) {
        if (scrollToTop != initialScrollToTop) listState.animateScrollToItem(0)
    }
}
