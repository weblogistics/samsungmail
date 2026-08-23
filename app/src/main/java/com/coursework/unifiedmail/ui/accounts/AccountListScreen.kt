package com.coursework.unifiedmail.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.R
import com.coursework.unifiedmail.data.local.AccountEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    onAddAccountClick: () -> Unit,
    onAccountClick: (accountId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: AccountListViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccountClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_account_title))
            }
        },
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_accounts_yet),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(accounts, key = { it.id }) { account ->
                    AccountRow(account, onClick = { onAccountClick(account.id) })
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: AccountEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(account.displayName) },
        supportingContent = { Text(account.emailAddress) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
