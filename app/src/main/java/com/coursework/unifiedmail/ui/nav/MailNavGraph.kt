package com.coursework.unifiedmail.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coursework.unifiedmail.ui.accounts.AccountListScreen
import com.coursework.unifiedmail.ui.compose.ComposeScreen
import com.coursework.unifiedmail.ui.inbox.InboxScreen
import com.coursework.unifiedmail.ui.inbox.UnifiedInboxScreen
import com.coursework.unifiedmail.ui.message.MessageDetailScreen
import com.coursework.unifiedmail.ui.onboarding.AddAccountScreen
import com.coursework.unifiedmail.ui.onboarding.EditAccountScreen
import com.coursework.unifiedmail.ui.settings.SettingsScreen

private object Routes {
    const val UNIFIED_INBOX = "unified_inbox"
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "add_account"
    const val EDIT_ACCOUNT = "edit_account/{accountId}"
    const val SETTINGS = "settings"
    const val INBOX = "inbox/{accountId}"
    const val MESSAGE_DETAIL = "message_detail/{accountId}/{uid}"
    const val COMPOSE = "compose/{accountId}/{mode}?sourceUid={sourceUid}"

    fun editAccount(accountId: String) = "edit_account/$accountId"
    fun inbox(accountId: String) = "inbox/$accountId"
    fun messageDetail(accountId: String, uid: Long) = "message_detail/$accountId/$uid"
    fun composeNew(accountId: String) = "compose/$accountId/new"
    fun composeReply(accountId: String, sourceUid: Long) = "compose/$accountId/reply?sourceUid=$sourceUid"
    fun composeForward(accountId: String, sourceUid: Long) = "compose/$accountId/forward?sourceUid=$sourceUid"
}

@Composable
fun MailNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.UNIFIED_INBOX) {
        composable(Routes.UNIFIED_INBOX) {
            UnifiedInboxScreen(
                onManageAccountsClick = { navController.navigate(Routes.ACCOUNTS) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAccountClick = { accountId -> navController.navigate(Routes.inbox(accountId)) },
                onMessageClick = { accountId, uid -> navController.navigate(Routes.messageDetail(accountId, uid)) },
                onComposeClick = { accountId -> navController.navigate(Routes.composeNew(accountId)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNTS) {
            AccountListScreen(
                onAddAccountClick = { navController.navigate(Routes.ADD_ACCOUNT) },
                onAccountClick = { accountId -> navController.navigate(Routes.editAccount(accountId)) },
            )
        }
        composable(Routes.ADD_ACCOUNT) {
            AddAccountScreen(
                onAccountSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDIT_ACCOUNT,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) {
            EditAccountScreen(
                onAccountSaved = { navController.popBackStack() },
                onAccountDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.INBOX,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val accountId = checkNotNull(backStackEntry.arguments?.getString("accountId"))
            InboxScreen(
                onBack = { navController.popBackStack() },
                onMessageClick = { uid -> navController.navigate(Routes.messageDetail(accountId, uid)) },
                onComposeClick = { navController.navigate(Routes.composeNew(accountId)) },
            )
        }
        composable(
            route = Routes.MESSAGE_DETAIL,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("uid") { type = NavType.LongType },
            ),
        ) {
            MessageDetailScreen(
                onBack = { navController.popBackStack() },
                onReply = { accountId, uid -> navController.navigate(Routes.composeReply(accountId, uid)) },
                onForward = { accountId, uid -> navController.navigate(Routes.composeForward(accountId, uid)) },
            )
        }
        composable(
            route = Routes.COMPOSE,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType },
                navArgument("sourceUid") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            ComposeScreen(
                onSent = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
