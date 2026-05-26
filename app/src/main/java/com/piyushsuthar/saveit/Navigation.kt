package com.piyushsuthar.saveit

import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.piyushsuthar.saveit.ui.chat.ChatListScreen
import com.piyushsuthar.saveit.ui.chat.ChatScreen
import com.piyushsuthar.saveit.ui.detail.NoteDetailScreen
import com.piyushsuthar.saveit.ui.group.GroupDetailScreen
import com.piyushsuthar.saveit.ui.main.MainScreen
import com.piyushsuthar.saveit.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    transitionSpec = {
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { fullWidth -> fullWidth }
        ) togetherWith slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { fullWidth -> -fullWidth }
        )
    },
    popTransitionSpec = {
        slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { fullWidth -> -fullWidth }
        ) togetherWith slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { fullWidth -> fullWidth }
        )
    },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) })
        }
        entry<Settings> {
          SettingsScreen(
            onBack = { backStack.removeLastOrNull() },
            onNavigateToAi = { backStack.add(SettingsAi) },
            onNavigateToData = { backStack.add(SettingsData) },
            onNavigateToDanger = { backStack.add(SettingsDanger) }
          )
        }
        entry<SettingsAi> {
          com.piyushsuthar.saveit.ui.settings.SettingsAiScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<SettingsData> {
          com.piyushsuthar.saveit.ui.settings.SettingsDataScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<SettingsDanger> {
          com.piyushsuthar.saveit.ui.settings.SettingsDangerScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<ChatList> {
          ChatListScreen(
            onBack = { backStack.removeLastOrNull() },
            onNavigateToChat = { id -> backStack.add(ChatThread(id)) }
          )
        }
        entry<ChatThread> { chatThread ->
          ChatScreen(
            threadId = chatThread.threadId,
            onBack = { backStack.removeLastOrNull() },
            onNavigateToDetail = { id -> backStack.add(NoteDetail(id)) }
          )
        }
        entry<GroupDetail> { groupDetail ->
          GroupDetailScreen(
            groupId = groupDetail.groupId,
            onBack = { backStack.removeLastOrNull() },
            onItemClick = { id -> backStack.add(NoteDetail(id)) }
          )
        }
        entry<NoteDetail> { noteDetail ->
          NoteDetailScreen(
            itemId = noteDetail.itemId, 
            onBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
