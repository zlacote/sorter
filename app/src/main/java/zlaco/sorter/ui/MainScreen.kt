package zlaco.sorter.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zlaco.sorter.R
import zlaco.sorter.data.MoveRecord

private enum class Tab(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SORT(R.string.tab_sort, Icons.Filled.Folder),
    HISTORY(R.string.tab_history, Icons.Filled.History),
    SETTINGS(R.string.tab_settings, Icons.Filled.Tune),
}

/**
 * Width ceiling for the content. A phone is narrower than this so nothing
 * changes there; on a tablet or a landscape window it stops a row of text and
 * buttons from stretching the whole way across the screen.
 */
private val CONTENT_MAX_WIDTH = 480.dp

/** Fully rounded ends: a pill, not a rectangle with soft corners. */
private val PILL_RADIUS = 28.dp

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.SORT) }

    val notice = state.notice
    val noticeText = when (notice) {
        is Notice.Moved -> context.getString(R.string.result_moved, notice.count)
        is Notice.NothingMoved ->
            if (notice.scanned > 0) {
                context.getString(R.string.result_nothing_movable, notice.scanned)
            } else {
                context.getString(R.string.result_sources_empty)
            }

        Notice.NeedsPermission -> context.getString(R.string.needs_permission)
        Notice.UndoFailed -> context.getString(R.string.undo_failed)
        null -> null
    }

    LaunchedEffect(notice) {
        if (noticeText != null) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(noticeText)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { PillNavigationBar(current = tab, onSelect = { tab = it }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                Tab.SORT -> SortTab(state, viewModel::setEnabled, onOpenSettings, viewModel::sortNow)
                Tab.HISTORY -> HistoryTab(state.moves, viewModel::undo)
                Tab.SETTINGS -> SettingsTab(state, viewModel)
            }
        }
    }
}

/**
 * Floating navigation rather than a Material bar.
 *
 * The standard NavigationBar spreads every destination evenly across the full
 * width, which reads as a list of settings rather than as navigation. Grouping
 * the two places you move between into one rounded pill and leaving the third
 * on its own beside it says "these two are siblings, that one is not" without a
 * word, and keeps the bar from being the widest thing on screen.
 */
@Composable
private fun PillNavigationBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(PILL_RADIUS))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.SORT.toPillItem(current, onSelect)
            Tab.HISTORY.toPillItem(current, onSelect)
        }

        Spacer(Modifier.width(10.dp))

        Tab.SETTINGS.toPillItem(current, onSelect, labelled = false)
    }
}

private fun Tab.toPillItem(
    current: Tab,
    onSelect: (Tab) -> Unit,
    labelled: Boolean = true,
) {
    val selected = this == current
    val shape = RoundedCornerShape(PILL_RADIUS)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .clickable { onSelect(this) }
            .padding(horizontal = if (labelled) 18.dp else 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (labelled) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shared shape for the tabs that are shorter than the screen: scrollable,
 * width-capped, and anchored to the top rather than centred, so a long settings
 * list starts where the eye expects instead of drifting down the page.
 */
@Composable
private fun ScrollableTab(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth(),
            content = content,
        )
    }
}

/**
 * The sorting screen: switch at the top, status in the middle, the one action
 * that matters at the very bottom.
 *
 * The flexible space between the three groups is what does it. Equal weights
 * centre the status in whatever room is left, and the button stays on the floor
 * of the screen no matter how short the content above it is. `heightIn` with
 * the viewport height is what lets weights and scrolling coexist: without it a
 * scrollable column has unbounded height, every weight would resolve to zero,
 * and the layout would silently fall back to a plain stack.
 */
@Composable
private fun FullHeightTab(
    top: @Composable ColumnScope.() -> Unit,
    middle: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth(),
                content = top,
            )
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth(),
                content = middle,
            )
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth(),
                content = bottom,
            )
        }
    }
}

@Composable
private fun SortTab(
    state: UiState,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSortNow: () -> Unit,
) {
    FullHeightTab(
        top = {
            Spacer(Modifier.height(24.dp))
            SwitchRow(
                title = stringResource(R.string.enable_title),
                summary = stringResource(R.string.enable_summary),
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
            )
            // Sits with the switch rather than at the bottom: it is the one
            // thing standing between the user and the app working at all.
            if (state.showPermissionAction) {
                Spacer(Modifier.height(12.dp))
                PermissionCard(onOpenSettings = onOpenSettings)
            }
        },
        middle = { StatusSection(state) },
        bottom = {
            OutlinedButton(
                onClick = onSortNow,
                enabled = !state.isWorking && state.hasPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (state.isWorking) R.string.working else R.string.sort_now),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        },
    )
}

@Composable
private fun HistoryTab(moves: List<MoveRecord>, onUndo: (MoveRecord) -> Unit) {
    if (moves.isEmpty()) {
        ScrollableTab {
            Spacer(Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // fillMaxWidth matters: the text would otherwise sit at the
                // left edge of a full width column and textAlign would have
                // nothing to centre within.
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = CONTENT_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.history_count, moves.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            items(moves, key = { it.movedAtMillis.toString() + it.displayName }) { move ->
                HistoryRow(move = move, onUndo = { onUndo(move) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsTab(state: UiState, viewModel: MainViewModel) {
    var newFolder by rememberSaveable { mutableStateOf("") }
    var targetDraft by rememberSaveable { mutableStateOf(state.targetRoot) }

    // Keep the field showing what is actually stored after an external change,
    // rather than whatever was typed before the screen was last built.
    LaunchedEffect(state.targetRoot) {
        if (viewModel.isTargetRoot(targetDraft)) targetDraft = state.targetRoot
    }

    ScrollableTab {
        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.sources_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.sources_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        state.sourceFolders.forEach { folder ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeSourceFolder(folder) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_source))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newFolder,
            onValueChange = { newFolder = it },
            label = { Text(stringResource(R.string.source_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (viewModel.addSourceFolder(newFolder)) newFolder = ""
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { if (viewModel.addSourceFolder(newFolder)) newFolder = "" },
            enabled = newFolder.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_source))
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = targetDraft,
            onValueChange = { targetDraft = it },
            label = { Text(stringResource(R.string.target_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { viewModel.setTargetRoot(targetDraft) },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.setTargetRoot(targetDraft) },
            enabled = targetDraft.isNotBlank() && !viewModel.isTargetRoot(targetDraft),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguage.options.forEach { option ->
                if (state.languageTag == option.tag) {
                    Button(onClick = { viewModel.setLanguage(option.tag) }) {
                        Text(stringResource(option.labelRes), textAlign = TextAlign.Center)
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.setLanguage(option.tag) }) {
                        Text(stringResource(option.labelRes), textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.permission_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@Composable
private fun StatusSection(state: UiState) {
    val run = state.lastRun

    val runLine = if (run == null) {
        stringResource(R.string.status_never)
    } else {
        val whenText = DateUtils.getRelativeTimeSpanString(
            run.atMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
        stringResource(R.string.status_line, whenText, run.moved, run.scanned)
    }

    // fillMaxWidth on the Card itself: a Card measures to its content, so a
    // column of short text leaves it a fraction of the row while the switch
    // above, which has a fillMaxWidth row inside it, spans the whole width.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = runLine, style = MaterialTheme.typography.bodyMedium)

            if (run != null && run.failures > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.status_failures, run.failures),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.isArmed) {
                    stringResource(R.string.status_armed)
                } else {
                    stringResource(R.string.status_not_armed)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(move: MoveRecord, onUndo: () -> Unit) {
    // fillMaxWidth is load-bearing: without it the column wraps to its text and
    // the row, and the divider under it, shrink into a narrow strip.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = move.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.history_item, move.fromRelativePath, move.toRelativePath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
        }
        HorizontalDivider()
    }
}
