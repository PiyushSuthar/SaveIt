package com.piyushsuthar.saveit.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyushsuthar.saveit.data.AiResult
import com.piyushsuthar.saveit.data.AppSettings
import com.piyushsuthar.saveit.data.DataRepository
import com.piyushsuthar.saveit.data.NetworkHelper
import com.piyushsuthar.saveit.data.SavedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class MainScreenViewModel(private val repository: DataRepository) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  private val _selectedCategory = MutableStateFlow("All")
  private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
  private val _isProcessing = MutableStateFlow(false)
  private val _thinkingSteps = MutableStateFlow<List<String>>(emptyList())

  val uiState: StateFlow<MainScreenUiState> = combine(
    repository.savedItems,
    repository.itemGroups,
    repository.settings,
    _searchQuery,
    _selectedCategory,
    _selectedItems,
    _isProcessing,
    _thinkingSteps
  ) { array ->
    val savedItems = array[0] as List<SavedItem>
    val itemGroups = array[1] as List<com.piyushsuthar.saveit.data.ItemGroup>
    val settings = array[2] as AppSettings
    val searchQuery = array[3] as String
    val selectedCategory = array[4] as String
    val selectedItems = array[5] as Set<String>
    val isProcessing = array[6] as Boolean
    val thinkingSteps = array[7] as List<String>

    val filteredItems = savedItems.filter { item ->
      val matchesQuery = searchQuery.isBlank() ||
        item.content.contains(searchQuery, ignoreCase = true) ||
        (item.title?.contains(searchQuery, ignoreCase = true) == true) ||
        (item.description?.contains(searchQuery, ignoreCase = true) == true) ||
        (item.summary?.contains(searchQuery, ignoreCase = true) == true)

      val matchesCategory = selectedCategory == "All" || item.category == selectedCategory

      matchesQuery && matchesCategory
    }

    val categoryCounts = mutableMapOf<String, Int>()
    categoryCounts["All"] = savedItems.size
    listOf("Note", "Article", "Video", "Social Media", "Shopping", "Code", "Other").forEach { cat ->
      categoryCounts[cat] = savedItems.count { it.category == cat }
    }

    MainScreenUiState.Success(
      savedItems = filteredItems,
      itemGroups = itemGroups,
      settings = settings,
      searchQuery = searchQuery,
      selectedCategory = selectedCategory,
      selectedItems = selectedItems,
      isProcessing = isProcessing,
      thinkingSteps = thinkingSteps,
      categoryCounts = categoryCounts
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = MainScreenUiState.Loading
  )

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun selectCategory(category: String) {
    _selectedCategory.value = category
  }

  fun toggleSelection(id: String) {
    val current = _selectedItems.value.toMutableSet()
    if (current.contains(id)) current.remove(id) else current.add(id)
    _selectedItems.value = current
  }

  fun clearSelection() {
    _selectedItems.value = emptySet()
  }

  fun deleteSelectedItems() {
    viewModelScope.launch {
      val itemsToDelete = _selectedItems.value
      _selectedItems.value = emptySet()
      itemsToDelete.forEach { id ->
        repository.deleteItem(id)
      }
    }
  }

  fun deleteItem(id: String) {
    viewModelScope.launch {
      repository.deleteItem(id)
    }
  }

  fun restoreItem(item: SavedItem) {
    viewModelScope.launch {
      repository.saveItem(item)
    }
  }

  fun submitInput(text: String) {
    if (text.isBlank()) return
    viewModelScope.launch {
      _isProcessing.value = true
      _thinkingSteps.value = listOf("🔍 Analyzing input text patterns...")
      try {
        kotlinx.coroutines.delay(450)
        val isLink = NetworkHelper.isUrl(text)
        val createdAt = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        val item = if (isLink) {
          val host = try { android.net.Uri.parse(text).host ?: "web" } catch (e: Throwable) { "web" }
          _thinkingSteps.value = _thinkingSteps.value + "🌐 Identified URL. Starting background HTML crawl for $host..."
          kotlinx.coroutines.delay(500)
          
          val metadata = NetworkHelper.fetchUrlMetadata(text)
          _thinkingSteps.value = _thinkingSteps.value + "📄 Metadata extracted: '${metadata.title.take(24)}...'"
          kotlinx.coroutines.delay(400)
          
          val settingsValue = repository.settings.value
          val aiResult = if (settingsValue.autoSummarize) {
            _thinkingSteps.value = _thinkingSteps.value + "✨ Opening secure on-device Gemini inference session..."
            kotlinx.coroutines.delay(500)
            _thinkingSteps.value = _thinkingSteps.value + "🤖 Processing context through model thinking mode..."
            kotlinx.coroutines.delay(500)
            
            val res = NetworkHelper.generateAiSummary(text, metadata, settingsValue)
            _thinkingSteps.value = _thinkingSteps.value + "🎯 Gemini finished! Folder category mapped: ${res.category}"
            kotlinx.coroutines.delay(400)
            res
          } else {
            _thinkingSteps.value = _thinkingSteps.value + "⚙️ Applying rules-based local categorization..."
            kotlinx.coroutines.delay(400)
            
            val res = AiResult(
              category = NetworkHelper.getLocalCategory(metadata.url, text),
              summary = metadata.description.ifBlank { "A link to ${metadata.host}" }
            )
            _thinkingSteps.value = _thinkingSteps.value + "📁 Category mapped: ${res.category}"
            kotlinx.coroutines.delay(300)
            res
          }

          SavedItem(
            id = id,
            content = metadata.url,
            title = metadata.title.ifBlank { metadata.host },
            description = metadata.description,
            imageUrl = metadata.imageUrl,
            category = aiResult.category,
            createdAt = createdAt,
            summary = aiResult.summary
          )
        } else {
          _thinkingSteps.value = _thinkingSteps.value + "📝 Identified text note. Parsing content length..."
          kotlinx.coroutines.delay(400)
          
          val settingsValue = repository.settings.value
          val aiResult = if (settingsValue.autoSummarize) {
            _thinkingSteps.value = _thinkingSteps.value + "✨ Opening secure on-device Gemini session..."
            kotlinx.coroutines.delay(500)
            _thinkingSteps.value = _thinkingSteps.value + "🤖 Summarizing content & classifying notes folder..."
            kotlinx.coroutines.delay(500)
            
            val res = NetworkHelper.generateAiSummary(text, null, settingsValue)
            _thinkingSteps.value = _thinkingSteps.value + "🎯 Gemini finished! Folder category mapped: ${res.category}"
            kotlinx.coroutines.delay(400)
            res
          } else {
            _thinkingSteps.value = _thinkingSteps.value + "📁 Categorization complete: Note Folder"
            kotlinx.coroutines.delay(300)
            AiResult(category = "Note", summary = text)
          }

          SavedItem(
            id = id,
            content = text,
            title = if (text.length > 30) text.take(30) + "..." else text,
            category = aiResult.category,
            createdAt = createdAt,
            summary = aiResult.summary
          )
        }

        _thinkingSteps.value = _thinkingSteps.value + "🗃 Writing encrypted data record to local storage..."
        kotlinx.coroutines.delay(400)
        
        repository.saveItem(item)
        _thinkingSteps.value = _thinkingSteps.value + "🎉 Saved successfully! Processing completed."
        kotlinx.coroutines.delay(500)
      } catch (e: Throwable) {
        _thinkingSteps.value = _thinkingSteps.value + "⚠️ Error during execution: ${e.message}"
        e.printStackTrace()
      } finally {
        _isProcessing.value = false
      }
    }
  }

  suspend fun suggestGroupNameForSelected(settings: AppSettings): String {
    return withContext(Dispatchers.IO) {
      val items = repository.savedItems.value.filter { it.id in _selectedItems.value }
      if (items.isEmpty()) return@withContext "New Group"

      // Create a context string for the LLM
      val contextString = items.take(10).joinToString("\n") { 
        "- Title: ${it.title ?: "No Title"}\n  Desc: ${it.description ?: "No Desc"}\n  Content: ${it.content.take(100)}" 
      }

      try {
        val tempSettings = settings.copy(
          chatSystemPrompt = "You are an assistant that suggests concise, accurate folder names. Reply ONLY with the exact suggested name, no quotes, no extra text."
        )
        val result = NetworkHelper.chatWithAi(
          history = listOf(com.piyushsuthar.saveit.data.ChatMessage("user", "Suggest a short, 2-4 word folder name for the following items:\n\n$contextString")),
          contextItems = emptyList(),
          settings = tempSettings
        )
        result
      } catch (e: Exception) {
        "New Group"
      }
    }
  }

  fun groupSelectedItems(groupName: String) {
    viewModelScope.launch {
      val selectedIds = _selectedItems.value
      if (selectedIds.isEmpty()) return@launch

      val groupId = UUID.randomUUID().toString()
      val newGroup = com.piyushsuthar.saveit.data.ItemGroup(
        id = groupId,
        name = groupName,
        createdAt = System.currentTimeMillis()
      )
      
      repository.saveItemGroup(newGroup)

      val itemsToUpdate = repository.savedItems.value
        .filter { it.id in selectedIds }
        .map { it.copy(groupId = groupId) }

      repository.saveItems(itemsToUpdate)
      _selectedItems.value = emptySet()
    }
  }

  fun addSelectedItemsToGroup(groupId: String) {
    viewModelScope.launch {
      val selectedIds = _selectedItems.value
      if (selectedIds.isEmpty()) return@launch

      val itemsToUpdate = repository.savedItems.value
        .filter { it.id in selectedIds }
        .map { it.copy(groupId = groupId) }

      repository.saveItems(itemsToUpdate)
      _selectedItems.value = emptySet()
    }
  }

  fun ungroupItems(groupId: String) {
    viewModelScope.launch {
      val itemsToUpdate = repository.savedItems.value
        .filter { it.groupId == groupId }
        .map { it.copy(groupId = null) }
      
      repository.saveItems(itemsToUpdate)
      repository.deleteItemGroup(groupId)
    }
  }
}

sealed class MainScreenUiState {
  data object Loading : MainScreenUiState()
  data class Success(
    val savedItems: List<SavedItem>,
    val itemGroups: List<com.piyushsuthar.saveit.data.ItemGroup>,
    val settings: AppSettings,
    val searchQuery: String,
    val selectedCategory: String,
    val selectedItems: Set<String>,
    val isProcessing: Boolean,
    val thinkingSteps: List<String>,
    val categoryCounts: Map<String, Int>
  ) : MainScreenUiState()
}
