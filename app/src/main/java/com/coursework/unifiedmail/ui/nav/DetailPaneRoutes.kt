package com.coursework.unifiedmail.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.coursework.unifiedmail.ui.components.EmptyState
import com.coursework.unifiedmail.ui.message.MessageDetailScreen
import com.coursework.unifiedmail.ui.thread.ThreadScreen

/**
 * Routes for the detail pane's own nested [NavHost] — separate from the outer graph in
 * [MailNavGraph] because this one only ever exists inside the wide-screen list-detail split
 * (see [MessageDetailPane]) and starts empty rather than at a real destination.
 */
private object DetailPaneRoutes {
    const val EMPTY = "empty"
    const val MESSAGE_DETAIL = "message_detail/{accountId}/{folderKey}/{uid}"
    const val THREAD = "thread/{accountId}/{folderKey}/{conversationId}"

    fun messageDetail(accountId: String, folderKey: String, uid: Long) =
        "message_detail/$accountId/${Uri.encode(folderKey)}/$uid"

    fun thread(accountId: String, folderKey: String, conversationId: String) =
        "thread/$accountId/${Uri.encode(folderKey)}/${Uri.encode(conversationId)}"
}

fun detailPaneMessageRoute(accountId: String, folderKey: String, uid: Long) =
    DetailPaneRoutes.messageDetail(accountId, folderKey, uid)

fun detailPaneThreadRoute(accountId: String, folderKey: String, conversationId: String) =
    DetailPaneRoutes.thread(accountId, folderKey, conversationId)

/**
 * The right-hand pane of the wide-screen list-detail split. Reuses [MessageDetailScreen] and
 * [ThreadScreen] (and their Hilt view models) completely unchanged — both read their args from
 * `SavedStateHandle`, which only Hilt-via-NavBackStackEntry can populate, so this nested
 * [NavHost] is what lets them be shown as a pane instead of a full-screen destination. Selecting
 * a row in the list pane navigates [navController] (see call sites in UnifiedInboxScreen /
 * InboxScreen) rather than the outer app-level controller; reply/reply-all/forward still bubble
 * out to the outer controller so Compose stays a full-screen flow over both panes.
 */
@Composable
fun MessageDetailPane(
    navController: NavHostController,
    onReply: (accountId: String, folderKey: String, uid: Long) -> Unit,
    onReplyAll: (accountId: String, folderKey: String, uid: Long) -> Unit,
    onForward: (accountId: String, folderKey: String, uid: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = DetailPaneRoutes.EMPTY,
        modifier = modifier,
    ) {
        composable(DetailPaneRoutes.EMPTY) {
            EmptyState(
                icon = Icons.Filled.Email,
                message = "Select a message to read",
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = DetailPaneRoutes.MESSAGE_DETAIL,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("folderKey") { type = NavType.StringType },
                navArgument("uid") { type = NavType.LongType },
            ),
        ) {
            MessageDetailScreen(
                onBack = { navController.popBackStack() },
                onReply = onReply,
                onReplyAll = onReplyAll,
                onForward = onForward,
            )
        }
        composable(
            route = DetailPaneRoutes.THREAD,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("folderKey") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val accountId = checkNotNull(backStackEntry.arguments?.getString("accountId"))
            ThreadScreen(
                onBack = { navController.popBackStack() },
                onMessageClick = { folderKey, uid ->
                    navController.navigate(DetailPaneRoutes.messageDetail(accountId, folderKey, uid))
                },
            )
        }
    }
}
