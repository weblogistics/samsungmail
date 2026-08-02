package com.coursework.unifiedmail.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.ui.components.SenderAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onBack: () -> Unit,
    onReply: (accountId: String, uid: Long) -> Unit,
    onForward: (accountId: String, uid: Long) -> Unit,
    viewModel: MessageDetailViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(message?.subject?.takeIf { it.isNotBlank() } ?: "(no subject)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            if (message != null) {
                BottomAppBar {
                    TextButton(
                        onClick = { onReply(viewModel.accountId, viewModel.uid) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                        Text(" Reply")
                    }
                    TextButton(
                        onClick = { onForward(viewModel.accountId, viewModel.uid) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
                        Text(" Forward")
                    }
                }
            }
        },
    ) { padding ->
        val current = message
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val senderLabel = current.fromPersonal?.takeIf { it.isNotBlank() }
                    ?: current.fromAddress ?: "Unknown sender"

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SenderAvatar(senderLabel, size = 48.dp)
                    Column {
                        Text(text = senderLabel, fontWeight = FontWeight.Bold)
                        current.toAddresses?.takeIf { it.isNotBlank() }?.let {
                            Text(text = "To: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(text = current.bodyText ?: current.bodyPreview)
            }
        }
    }
}
