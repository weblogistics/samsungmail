package com.coursework.unifiedmail.ui.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.DraftEntity
import com.coursework.unifiedmail.domain.richtext.RichText
import com.coursework.unifiedmail.ui.components.EmptyState
import com.coursework.unifiedmail.ui.components.formatRelativeDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onBack: () -> Unit,
    onDraftClick: (accountId: String, draftId: String) -> Unit,
    viewModel: DraftListViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drafts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (drafts.isEmpty()) {
            EmptyState(icon = Icons.Filled.Drafts, message = "No drafts", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(drafts, key = { it.id }) { draft ->
                    DraftRow(
                        draft = draft,
                        onClick = { onDraftClick(draft.accountId, draft.id) },
                        onDelete = { viewModel.deleteDraft(draft.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DraftRow(draft: DraftEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val bodyPreview = RichText.plainTextOf(RichText.fromHtml(draft.bodyHtml)).replace('\n', ' ').trim()
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(draft.subject.ifBlank { "(no subject)" }, maxLines = 1) },
        supportingContent = {
            Text(
                text = listOfNotNull(draft.to.takeIf { it.isNotBlank() }, bodyPreview.takeIf { it.isNotBlank() }).joinToString(" — "),
                maxLines = 1,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeDate(draft.updatedAtEpochMillis))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete draft")
                }
            }
        },
    )
}
