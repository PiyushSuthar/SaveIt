package com.piyushsuthar.saveit.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface DataRepository {
  val savedItems: StateFlow<List<SavedItem>>
  val settings: StateFlow<AppSettings>
  val chatThreads: StateFlow<List<ChatThread>>
  val itemGroups: StateFlow<List<ItemGroup>>

  suspend fun saveItem(item: SavedItem)
  suspend fun saveItems(items: List<SavedItem>)
  suspend fun deleteItem(id: String)
  suspend fun saveChatThread(thread: ChatThread)
  suspend fun deleteChatThread(id: String)
  suspend fun saveItemGroup(group: ItemGroup)
  suspend fun deleteItemGroup(id: String)
  suspend fun updateSettings(settings: AppSettings)
  suspend fun clearAllData()
  suspend fun importBackup(jsonString: String): Boolean
  fun exportBackup(): String
}

class DefaultDataRepository private constructor(private val context: Context) : DataRepository {

  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    coerceInputValues = true
  }

  private val itemsFile = File(context.filesDir, "saved_items.json")
  private val settingsFile = File(context.filesDir, "app_settings.json")
  private val chatsFile = File(context.filesDir, "chat_threads.json")
  private val itemGroupsFile = File(context.filesDir, "item_groups.json")

  private val _savedItems = MutableStateFlow<List<SavedItem>>(emptyList())
  override val savedItems: StateFlow<List<SavedItem>> = _savedItems.asStateFlow()

  private val _settings = MutableStateFlow(AppSettings())
  override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private val _chatThreads = MutableStateFlow<List<ChatThread>>(emptyList())
  override val chatThreads: StateFlow<List<ChatThread>> = _chatThreads.asStateFlow()

  private val _itemGroups = MutableStateFlow<List<ItemGroup>>(emptyList())
  override val itemGroups: StateFlow<List<ItemGroup>> = _itemGroups.asStateFlow()

  private val itemsMutex = Mutex()
  private val settingsMutex = Mutex()
  private val chatsMutex = Mutex()
  private val groupsMutex = Mutex()
  private val scope = CoroutineScope(Dispatchers.IO)

  init {
    loadSettings()
    loadItems()
    loadChats()
    loadGroups()
  }

  private fun loadGroups() {
    scope.launch {
      groupsMutex.withLock {
        try {
          if (itemGroupsFile.exists()) {
            val content = itemGroupsFile.readText()
            val parsed = json.decodeFromString<List<ItemGroup>>(content)
            _itemGroups.value = parsed.sortedByDescending { it.createdAt }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
  }

  private fun loadChats() {
    scope.launch {
      chatsMutex.withLock {
        try {
          if (chatsFile.exists()) {
            val content = chatsFile.readText()
            val parsed = json.decodeFromString<List<ChatThread>>(content)
            _chatThreads.value = parsed.sortedByDescending { it.createdAt }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
  }

  private fun loadSettings() {
    scope.launch {
      settingsMutex.withLock {
        try {
          if (settingsFile.exists()) {
            val content = settingsFile.readText()
            val parsed = json.decodeFromString<AppSettings>(content)
            _settings.value = parsed
          } else {
            // Write default settings
            val content = json.encodeToString(AppSettings())
            settingsFile.writeText(content)
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
  }

  private fun loadItems() {
    scope.launch {
      itemsMutex.withLock {
        try {
          if (itemsFile.exists()) {
            val content = itemsFile.readText()
            val parsed = json.decodeFromString<List<SavedItem>>(content)
            _savedItems.value = parsed.sortedByDescending { it.createdAt }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
  }

  override suspend fun saveItem(item: SavedItem) {
    itemsMutex.withLock {
      try {
        val currentList = _savedItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index >= 0) {
          currentList[index] = item
        } else {
          currentList.add(item)
        }
        val sortedList = currentList.sortedByDescending { it.createdAt }
        itemsFile.writeText(json.encodeToString(sortedList))
        _savedItems.value = sortedList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun saveItems(items: List<SavedItem>) {
    itemsMutex.withLock {
      try {
        val currentList = _savedItems.value.toMutableList()
        for (item in items) {
          val index = currentList.indexOfFirst { it.id == item.id }
          if (index >= 0) {
            currentList[index] = item
          } else {
            currentList.add(item)
          }
        }
        val sortedList = currentList.sortedByDescending { it.createdAt }
        itemsFile.writeText(json.encodeToString(sortedList))
        _savedItems.value = sortedList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun deleteItem(id: String) {
    itemsMutex.withLock {
      try {
        val currentList = _savedItems.value.filter { it.id != id }
        itemsFile.writeText(json.encodeToString(currentList))
        _savedItems.value = currentList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun saveItemGroup(group: ItemGroup) {
    groupsMutex.withLock {
      try {
        val currentList = _itemGroups.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == group.id }
        if (index >= 0) {
          currentList[index] = group
        } else {
          currentList.add(group)
        }
        val sortedList = currentList.sortedByDescending { it.createdAt }
        itemGroupsFile.writeText(json.encodeToString(sortedList))
        _itemGroups.value = sortedList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun deleteItemGroup(id: String) {
    groupsMutex.withLock {
      try {
        val currentList = _itemGroups.value.filter { it.id != id }
        itemGroupsFile.writeText(json.encodeToString(currentList))
        _itemGroups.value = currentList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun saveChatThread(thread: ChatThread) {
    chatsMutex.withLock {
      try {
        val currentList = _chatThreads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == thread.id }
        if (index >= 0) {
          currentList[index] = thread
        } else {
          currentList.add(thread)
        }
        val sortedList = currentList.sortedByDescending { it.createdAt }
        chatsFile.writeText(json.encodeToString(sortedList))
        _chatThreads.value = sortedList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun deleteChatThread(id: String) {
    chatsMutex.withLock {
      try {
        val currentList = _chatThreads.value.filter { it.id != id }
        chatsFile.writeText(json.encodeToString(currentList))
        _chatThreads.value = currentList
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun updateSettings(settings: AppSettings) {
    settingsMutex.withLock {
      try {
        settingsFile.writeText(json.encodeToString(settings))
        _settings.value = settings
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun clearAllData() {
    itemsMutex.withLock {
      try {
        if (itemsFile.exists()) itemsFile.delete()
        _savedItems.value = emptyList()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    chatsMutex.withLock {
      try {
        if (chatsFile.exists()) chatsFile.delete()
        _chatThreads.value = emptyList()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    groupsMutex.withLock {
      try {
        if (itemGroupsFile.exists()) itemGroupsFile.delete()
        _itemGroups.value = emptyList()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override suspend fun importBackup(jsonString: String): Boolean {
    return itemsMutex.withLock {
      try {
        val parsed = json.decodeFromString<List<SavedItem>>(jsonString)
        val mergedList = (_savedItems.value + parsed)
          .distinctBy { it.id }
          .sortedByDescending { it.createdAt }
        itemsFile.writeText(json.encodeToString(mergedList))
        _savedItems.value = mergedList
        true
      } catch (e: Exception) {
        e.printStackTrace()
        false
      }
    }
  }

  override fun exportBackup(): String {
    return try {
      json.encodeToString(_savedItems.value)
    } catch (e: Exception) {
      e.printStackTrace()
      "[]"
    }
  }

  companion object {
    @Volatile
    private var INSTANCE: DefaultDataRepository? = null

    fun initialize(context: Context) {
      if (INSTANCE == null) {
        synchronized(this) {
          if (INSTANCE == null) {
            INSTANCE = DefaultDataRepository(context.applicationContext)
          }
        }
      }
    }

    fun getInstance(): DefaultDataRepository {
      return INSTANCE ?: throw IllegalStateException("DefaultDataRepository has not been initialized. Call initialize(context) first.")
    }
  }
}
