package com.piyushsuthar.saveit

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Settings : NavKey
@Serializable data object SettingsAi : NavKey
@Serializable data object SettingsData : NavKey
@Serializable data object SettingsDanger : NavKey
@Serializable data object Chat : NavKey
@Serializable data object ChatList : NavKey
@Serializable data class ChatThread(val threadId: String) : NavKey
@Serializable data class GroupDetail(val groupId: String) : NavKey
@Serializable data class NoteDetail(val itemId: String) : NavKey
