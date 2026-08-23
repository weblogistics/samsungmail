package com.coursework.unifiedmail.ui.nav

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.ui.accounts.AccountListScreen
import com.coursework.unifiedmail.ui.compose.ComposeScreen
import com.coursework.unifiedmail.ui.drafts.DraftListScreen
import com.coursework.unifiedmail.ui.folders.FolderListScreen
import com.coursework.unifiedmail.ui.inbox.InboxScreen
import com.coursework.unifiedmail.ui.inbox.UnifiedInboxScreen
import com.coursework.unifiedmail.ui.message.MessageDetailScreen
import com.coursework.unifiedmail.ui.onboarding.AddAccountScreen
import com.coursework.unifiedmail.ui.onboarding.EditAccountScreen
import com.coursework.unifiedmail.ui.settings.SettingsScreen
import com.coursework.unifiedmail.ui.thread.ThreadScreen

// Folder keys are IMAP folder names and can contain characters path segments can't (notably "/"
// — e.g. Gmail's "[Gmail]/Sent Mail") — always Uri.encode() going into a route and rely on
// Navigation's own decoding when reading the argument back out.
private object Routes {
    const val UNIFIED_INBOX = "unified_inbox"
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "add_account"
    const val EDIT_ACCOUNT = "edit_account/{accountId}"
    const val SETTINGS = "settings"
    const val DRAFTS = "drafts"
    const val FOLDERS = "folders/{accountId}"
    const val INBOX = "inbox/{accountId}/{folderKey}"
    const val MESSAGE_DETAIL = "message_detail/{accountId}/{folderKey}/{uid}"
    const val THREAD = "thread/{accountId}/{folderKey}/{conversationId}"
    const val COMPOSE = "compose/{accountId}/{mode}?sourceUid={sourceUid}&sourceFolderKey={sourceFolderKey}&draftId={draftId}"

    fun editAccount(accountId: String) = "edit_account/$accountId"
    fun folders(accountId: String) = "folders/$accountId"
    fun inbox(accountId: String, folderKey: String = MailRepository.INBOX_FOLDER_KEY) =
        "inbox/$accountId/${Uri.encode(folderKey)}"
    fun messageDetail(accountId: String, folderKey: String, uid: Long) =
        "message_detail/$accountId/${Uri.encode(folderKey)}/$uid"
    fun thread(accountId: String, folderKey: String, conversationId: String) =
        "thread/$accountId/${Uri.encode(folderKey)}/${Uri.encode(conversationId)}"
    fun composeNew(accountId: String) = "compose/$accountId/new"
    fun composeReply(accountId: String, folderKey: String, sourceUid: Long) =
        "compose/$accountId/reply?sourceUid=$sourceUid&sourceFolderKey=${Uri.encode(folderKey)}"
    fun composeReplyAll(accountId: String, folderKey: String, sourceUid: Long) =
        "compose/$accountId/reply_all?sourceUid=$sourceUid&sourceFolderKey=${Uri.encode(folderKey)}"
    fun composeForward(accountId: String, folderKey: String, sourceUid: Long) =
        "compose/$accountId/forward?sourceUid=$sourceUid&sourceFolderKey=${Uri.encode(folderKey)}"
    fun composeDraft(accountId: String, draftId: String) = "compose/$accountId/new?draftId=${Uri.encode(draftId)}"
}

@Composable
fun MailNavGraph(startAccountId: String? = null, navController: NavHostController = rememberNavController()) {
    // Unified Inbox stays the true start destination (a stable back-stack root that never
    // dead-ends if the configured default account is later deleted) — a configured
    // single-mailbox default just auto-navigates on top of it once, exactly like tapping that
    // account from the drawer would. See AppSettings.defaultViewAccountId.
    LaunchedEffect(startAccountId) {
        if (startAccountId != null) {
            navController.navigate(Routes.inbox(startAccountId))
        }
    }

    // A shared, subtle slide+fade for every route rather than the previous instant cut — forward
    // navigation slides in from the right, back navigation slides out to the right, both fading.
    NavHost(
        navController = navController,
        startDestination = Routes.UNIFIED_INBOX,
        enterTransition = { slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(150)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(220)) { it / 4 } + fadeOut(tween(220)) },
    ) {
        composable(Routes.UNIFIED_INBOX) {
            UnifiedInboxScreen(
                onManageAccountsClick = { navController.navigate(Routes.ACCOUNTS) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAccountClick = { accountId -> navController.navigate(Routes.folders(accountId)) },
                onDraftsClick = { navController.navigate(Routes.DRAFTS) },
                onMessageClick = { accountId, uid ->
                    navController.navigate(Routes.messageDetail(accountId, MailRepository.INBOX_FOLDER_KEY, uid))
                },
                onThreadClick = { accountId, conversationId ->
                    navController.navigate(Routes.thread(accountId, MailRepository.INBOX_FOLDER_KEY, conversationId))
                },
                onComposeClick = { accountId -> navController.navigate(Routes.composeNew(accountId)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DRAFTS) {
            DraftListScreen(
                onBack = { navController.popBackStack() },
                onDraftClick = { accountId, draftId -> navController.navigate(Routes.composeDraft(accountId, draftId)) },
            )
        }
        composable(Routes.ACCOUNTS) {
            AccountListScreen(
                onAddAccountClick = { navController.navigate(Routes.ADD_ACCOUNT) },
                onAccountClick = { accountId -> navController.navigate(Routes.editAccount(accountId)) },
                onBack = { navController.popBackStack() },
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
            route = Routes.FOLDERS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val accountId = checkNotNull(backStackEntry.arguments?.getString("accountId"))
            FolderListScreen(
                onBack = { navController.popBackStack() },
                onFolderClick = { folderKey -> navController.navigate(Routes.inbox(accountId, folderKey)) },
            )
        }
        composable(
            route = Routes.INBOX,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("folderKey") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val accountId = checkNotNull(backStackEntry.arguments?.getString("accountId"))
            InboxScreen(
                onBack = { navController.popBackStack() },
                onMessageClick = { folderKey, uid ->
                    navController.navigate(Routes.messageDetail(accountId, folderKey, uid))
                },
                onThreadClick = { folderKey, conversationId ->
                    navController.navigate(Routes.thread(accountId, folderKey, conversationId))
                },
                onComposeClick = { navController.navigate(Routes.composeNew(accountId)) },
            )
        }
        composable(
            route = Routes.THREAD,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("folderKey") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val accountId = checkNotNull(backStackEntry.arguments?.getString("accountId"))
            ThreadScreen(
                onBack = { navController.popBackStack() },
                onMessageClick = { threadFolderKey, uid ->
                    navController.navigate(Routes.messageDetail(accountId, threadFolderKey, uid))
                },
            )
        }
        composable(
            route = Routes.MESSAGE_DETAIL,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("folderKey") { type = NavType.StringType },
                navArgument("uid") { type = NavType.LongType },
            ),
        ) {
            MessageDetailScreen(
                onBack = { navController.popBackStack() },
                onReply = { accountId, folderKey, uid ->
                    navController.navigate(Routes.composeReply(accountId, folderKey, uid))
                },
                onReplyAll = { accountId, folderKey, uid ->
                    navController.navigate(Routes.composeReplyAll(accountId, folderKey, uid))
                },
                onForward = { accountId, folderKey, uid ->
                    navController.navigate(Routes.composeForward(accountId, folderKey, uid))
                },
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
                navArgument("sourceFolderKey") {
                    type = NavType.StringType
                    defaultValue = MailRepository.INBOX_FOLDER_KEY
                },
                navArgument("draftId") {
                    type = NavType.StringType
                    defaultValue = ""
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
