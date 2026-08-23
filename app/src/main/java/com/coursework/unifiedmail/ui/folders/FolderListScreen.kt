package com.coursework.unifiedmail.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.domain.folders.FolderTreeNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    onBack: () -> Unit,
    onFolderClick: (folderKey: String) -> Unit,
    viewModel: FolderListViewModel = hiltViewModel(),
) {
    val visibleFolders by viewModel.visibleFolders.collectAsState()
    val collapsedFolderFullNames by viewModel.collapsedFolderFullNames.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (error != null && visibleFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            } else if (visibleFolders.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(visibleFolders, key = { it.folder.id }) { node ->
                        FolderTreeRow(
                            node = node,
                            isExpanded = node.folder.fullName !in collapsedFolderFullNames,
                            onClick = { onFolderClick(MailRepository.localFolderKey(node.folder.fullName)) },
                            onToggleExpanded = { viewModel.toggleExpanded(node.folder.fullName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTreeRow(
    node: FolderTreeNode,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = {
            Row(modifier = Modifier.padding(start = (node.depth * 16).dp)) {
                Text(node.folder.displayName)
            }
        },
        // Tapping the row (label) always navigates into that folder's Inbox — a folder can hold
        // both messages and subfolders, so expanding/collapsing is a separate affordance.
        trailingContent = if (node.children.isNotEmpty()) {
            {
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                    )
                }
            }
        } else {
            null
        },
    )
}
