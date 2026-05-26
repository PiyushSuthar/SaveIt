package com.piyushsuthar.saveit.data

import kotlinx.serialization.Serializable

@Serializable
data class SavedItem(
  val id: String,
  val content: String, // The original link or note text
  val title: String? = null,
  val description: String? = null,
  val imageUrl: String? = null,
  val category: String, // "Note", "Article", "Video", "Social Media", "Shopping", "Code", "Other"
  val tags: List<String> = emptyList(),
  val createdAt: Long,
  val summary: String? = null,
  val groupId: String? = null
)

@Serializable
data class ItemGroup(
  val id: String,
  val name: String,
  val createdAt: Long
)

@Serializable
data class ChatMessage(
  val role: String,
  val content: String,
  val embeddedItems: List<SavedItem> = emptyList()
)

@Serializable
data class ChatThread(
  val id: String,
  val title: String,
  val messages: List<ChatMessage>,
  val createdAt: Long
)

@Serializable
data class AppSettings(
  val apiProvider: String = "Gemini", // "Gemini" or "Custom (OpenAI Compatible)"
  val apiBaseUrl: String = "",
  val apiKey: String = "",
  val selectedModel: String = "gemini-1.5-flash",
  val systemPrompt: String = "You are a helpful assistant that categorizes and summarizes notes and link content. Analyze the input content. Respond ONLY with a JSON object containing two fields:\n1. \"category\": a short string classifying the content (choose ONLY from: \"Article\", \"Video\", \"Social Media\", \"Shopping\", \"Code\", \"Note\", \"Other\").\n2. \"summary\": a clean, engaging 1-2 sentence summary or key takeaway of the content.",
  val chatSystemPrompt: String = "You are an intelligent assistant. You answer questions based on the user's saved notes and links (context).\nIMPORTANT: If you mention, recommend, or refer to a specific saved item, you MUST include its ID in your response exactly like this: [ITEM:id]. You can include multiple items.\nIf the user asks you to save a link, output exactly [SAVE_LINK:https://example.com] in your response.",
  val autoSummarize: Boolean = true
)
