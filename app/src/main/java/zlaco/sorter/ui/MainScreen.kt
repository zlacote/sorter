package zlaco.sorter.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zlaco.sorter.R
import zlaco.sorter.data.MoveRecord

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notice = state.notice
    val noticeText = when (notice) {
        is Notice.Moved -> context.getString(R.string.result_moved, notice.count)
        Notice.NothingToDo -> context.getString(R.string.result_nothing)
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.enable_title),
                    summary = stringResource(R.string.enable_summary),
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }

            if (state.showPermissionAction) {
                item { PermissionCard(onOpenSettings = onOpenSettings) }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::sortNow,
                    enabled = !state.isWorking && state.hasPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sort_now))
                }
            }

            item {
                SwitchRow(
                    title = stringResource(R.string.recordings_title),
                    summary = stringResource(R.string.recordings_summary),
                    checked = state.includeRecordings,
                    onCheckedChange = viewModel::setIncludeRecordings,
                )
            }

            item { StatusSection(state) }

            item {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.moves.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.moves, key = { it.mediaId }) { move ->
                    HistoryRow(move = move, onUndo = { viewModel.undo(move) })
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
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
            Button(onClick = onOpenSettings) {
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

    Card {
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

            if (state.isWorking) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.working), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryRow(move: MoveRecord, onUndo: () -> Unit) {
    Column {
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
