package com.piyushsuthar.saveit.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyushsuthar.saveit.data.ChatMessage
import com.piyushsuthar.saveit.data.ChatThread
import com.piyushsuthar.saveit.data.DataRepository
import com.piyushsuthar.saveit.data.NetworkHelper
import com.piyushsuthar.saveit.data.SavedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val repository: DataRepository) : ViewModel() {

  val chatThreads = repository.chatThreads

  private val _selectedThreadId = MutableStateFlow<String?>(null)
  val selectedThreadId: StateFlow<String?> = _selectedThreadId.asStateFlow()

  private val _messages = MutableStateFlow<List<ChatMessage>>(
    listOf(ChatMessage("assistant", "Hi! I'm your memory assistant. Ask me anything about your saved links and notes."))
  )
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isProcessing = MutableStateFlow(false)
  val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

  fun selectThread(threadId: String?) {
    _selectedThreadId.value = threadId
    if (threadId == null) {
      _messages.value = listOf(ChatMessage("assistant", "Hi! I'm your memory assistant. Ask me anything about your saved links and notes."))
    } else {
      val thread = chatThreads.value.find { it.id == threadId }
      if (thread != null) {
        _messages.value = thread.messages
      }
    }
  }

  fun createNewThread(): String {
    val newId = UUID.randomUUID().toString()
    val newThread = ChatThread(
      id = newId,
      title = "New Chat",
      messages = listOf(ChatMessage("assistant", "Hi! I'm your memory assistant. Ask me anything about your saved links and notes.")),
      createdAt = System.currentTimeMillis()
    )
    viewModelScope.launch {
      repository.saveChatThread(newThread)
    }
    return newId
  }

  fun deleteThread(threadId: String) {
    viewModelScope.launch {
      repository.deleteChatThread(threadId)
      if (_selectedThreadId.value == threadId) {
        selectThread(null)
      }
    }
  }

  fun sendMessage(text: String) {
    if (text.isBlank()) return
    val userMsg = ChatMessage("user", text)
    _messages.value = _messages.value + userMsg

    // Ensure we have a thread before sending
    var currentThreadId = _selectedThreadId.value
    if (currentThreadId == null) {
      currentThreadId = UUID.randomUUID().toString()
      _selectedThreadId.value = currentThreadId
      viewModelScope.launch {
        val newThread = ChatThread(
          id = currentThreadId,
          title = text.take(30) + if (text.length > 30) "..." else "",
          messages = _messages.value,
          createdAt = System.currentTimeMillis()
        )
        repository.saveChatThread(newThread)
      }
    } else {
      updateCurrentThread()
    }

    viewModelScope.launch {
      _isProcessing.value = true
      val contextItems = repository.savedItems.value
      val settings = repository.settings.value

      // Using the updated chatWithAi which takes the history instead of just text
      val rawReply = NetworkHelper.chatWithAi(_messages.value, contextItems, settings, repository)
      
      // Parse [ITEM:id]
      val itemRegex = Regex("\\[ITEM:(.*?)\\]")
      val itemIds = itemRegex.findAll(rawReply).map { it.groupValues[1] }.toSet()
      
      // Parse [SAVE_LINK:url]
      val saveLinkRegex = Regex("\\[SAVE_LINK:(.*?)\\]")
      val linksToSave = saveLinkRegex.findAll(rawReply).map { it.groupValues[1] }.toList()

      var cleanReply = rawReply
        .replace(itemRegex, "")
        .replace(saveLinkRegex, "*(Saving link...)*")
        .trim()
      
      val embeddedItems = contextItems.filter { it.id in itemIds }
      _messages.value = _messages.value + ChatMessage("assistant", cleanReply, embeddedItems)
      updateCurrentThread()

      // Process saving links
      if (linksToSave.isNotEmpty()) {
        linksToSave.forEach { url ->
          try {
            val meta = NetworkHelper.fetchUrlMetadata(url)
            val aiData = NetworkHelper.generateAiSummary(url, meta, settings)
            val newItem = SavedItem(
              id = UUID.randomUUID().toString(),
              content = url,
              title = meta.title,
              description = meta.description,
              imageUrl = meta.imageUrl,
              category = aiData.category,
              summary = aiData.summary,
              createdAt = System.currentTimeMillis()
            )
            repository.saveItem(newItem)
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
        cleanReply = cleanReply.replace("*(Saving link...)*", "*(Link saved successfully!)*")
        val finalMessages = _messages.value.toMutableList()
        finalMessages[finalMessages.lastIndex] = ChatMessage("assistant", cleanReply, embeddedItems)
        _messages.value = finalMessages
        updateCurrentThread()
      }

      _isProcessing.value = false
    }
  }

  private fun updateCurrentThread() {
    val currentThreadId = _selectedThreadId.value ?: return
    viewModelScope.launch {
      val existing = chatThreads.value.find { it.id == currentThreadId }
      if (existing != null) {
        repository.saveChatThread(existing.copy(messages = _messages.value))
      }
    }
  }
}
