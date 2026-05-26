package com.piyushsuthar.saveit.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

@Serializable
data class GeminiRequest(
  val systemInstruction: GeminiContent? = null,
  val contents: List<GeminiContent>,
  val generationConfig: GeminiGenerationConfig? = null,
  val tools: List<GeminiTool>? = null
)

@Serializable
data class GeminiContent(
  val role: String? = null,
  val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
  val text: String? = null,
  val functionCall: GeminiFunctionCall? = null,
  val functionResponse: GeminiFunctionResponse? = null
)

@Serializable
data class GeminiFunctionCall(
  val name: String,
  val args: kotlinx.serialization.json.JsonObject
)

@Serializable
data class GeminiFunctionResponse(
  val name: String,
  val response: kotlinx.serialization.json.JsonObject
)

@Serializable
data class GeminiTool(
  val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
  val name: String,
  val description: String,
  val parameters: GeminiSchema? = null
)

@Serializable
data class GeminiSchema(
  val type: String,
  val properties: Map<String, GeminiSchemaProperty>? = null,
  val required: List<String>? = null
)

@Serializable
data class GeminiSchemaProperty(
  val type: String,
  val description: String? = null
)

@Serializable
data class GeminiGenerationConfig(
  val responseMimeType: String? = null
)

@Serializable
data class AiResponseSchema(
  val category: String,
  val summary: String
)

@Serializable
data class OpenAiRequest(
  val model: String,
  val messages: List<OpenAiMessage>,
  val response_format: OpenAiResponseFormat? = null
)

@Serializable
data class OpenAiMessage(
  val role: String,
  val content: String
)

@Serializable
data class OpenAiResponseFormat(
  val type: String
)

data class UrlMetadata(
  val url: String,
  val host: String,
  val title: String = "",
  val description: String = "",
  val imageUrl: String? = null
)

data class AiResult(
  val category: String,
  val summary: String
)

object NetworkHelper {

  private val json = Json { ignoreUnknownKeys = true }

  fun isUrl(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.contains(" ") || trimmed.contains("\n")) return false
    return trimmed.startsWith("http://", ignoreCase = true) ||
           trimmed.startsWith("https://", ignoreCase = true) ||
           trimmed.matches(Regex("^(www\\.)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))
  }

  suspend fun fetchUrlMetadata(urlString: String): UrlMetadata = withContext(Dispatchers.IO) {
    val trimmed = urlString.trim()
    val cleanUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
      "https://$trimmed"
    } else {
      trimmed
    }

    try {
      val url = URL(cleanUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
      connection.connectTimeout = 5000
      connection.readTimeout = 5000

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        return@withContext UrlMetadata(url = cleanUrl, host = url.host ?: "")
      }

      val reader = BufferedReader(InputStreamReader(connection.inputStream))
      val sb = StringBuilder()
      var line: String?
      var bytesRead = 0
      // Read only the first 100KB to parse the HTML head tags
      while (reader.readLine().also { line = it } != null && bytesRead < 102400) {
        sb.append(line).append("\n")
        bytesRead += line?.length ?: 0
      }
      reader.close()
      connection.disconnect()

      val html = sb.toString()
      val title = extractTag(html, "<title>(.*?)</title>")
        ?: extractMetaTag(html, "og:title")
        ?: extractMetaTag(html, "twitter:title")
        ?: ""

      val description = extractMetaTag(html, "description")
        ?: extractMetaTag(html, "og:description")
        ?: extractMetaTag(html, "twitter:description")
        ?: ""

      val imageUrl = extractMetaTag(html, "og:image")
        ?: extractMetaTag(html, "twitter:image")

      UrlMetadata(
        url = cleanUrl,
        host = url.host ?: "",
        title = title.trim(),
        description = description.trim(),
        imageUrl = imageUrl?.trim()
      )
    } catch (e: Throwable) {
      Log.e("NetworkHelper", "Error fetching URL metadata for $cleanUrl: ${e.message}")
      val host = try { URL(cleanUrl).host ?: "" } catch (ex: Throwable) { "" }
      UrlMetadata(url = cleanUrl, host = host)
    }
  }

  private fun extractTag(html: String, regex: String): String? {
    try {
      if (regex.contains("<title>")) {
        val start = html.indexOf("<title", ignoreCase = true)
        if (start != -1) {
          val closeBracket = html.indexOf(">", start)
          if (closeBracket != -1) {
            val end = html.indexOf("</title>", closeBracket, ignoreCase = true)
            if (end != -1) {
              return unescapeHtml(html.substring(closeBracket + 1, end).trim())
            }
          }
        }
      }
      val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
      val matcher = pattern.matcher(html)
      if (matcher.find()) {
        return unescapeHtml(matcher.group(1))
      }
    } catch (e: Throwable) {
      // Ignored
    }
    return null
  }

  private fun extractMetaTag(html: String, name: String): String? {
    try {
      val searchStr = html.lowercase()
      val nameLower = name.lowercase()
      var idx = 0
      while (true) {
        idx = searchStr.indexOf("<meta ", idx)
        if (idx == -1) break
        val endIdx = searchStr.indexOf(">", idx)
        if (endIdx == -1) break
        
        val tag = html.substring(idx, endIdx + 1)
        val tagLower = tag.lowercase()
        
        // Only check this tag if it contains our target name
        if (tagLower.contains(nameLower)) {
          // Now use regex just on this short 100-char string
          val regex = "content\\s*=\\s*[\"'](.*?)[\"']"
          val matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(tag)
          if (matcher.find()) {
            return unescapeHtml(matcher.group(1))
          }
        }
        idx = endIdx + 1
      }
    } catch (e: Throwable) {
      // Ignored
    }
    return null
  }

  private fun unescapeHtml(str: String?): String? {
    if (str == null) return null
    return str
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
  }

  fun getLocalCategory(url: String?, text: String): String {
    if (url == null) return "Note"
    val lower = url.lowercase()
    return when {
      lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("vimeo.com") -> "Video"
      lower.contains("github.com") || lower.contains("stackoverflow.com") || lower.contains("gitlab.com") -> "Code"
      lower.contains("amazon.com") || lower.contains("ebay.com") || lower.contains("aliexpress.com") || lower.contains("target.com") -> "Shopping"
      lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("reddit.com") || lower.contains("instagram.com") || lower.contains("facebook.com") || lower.contains("linkedin.com") -> "Social Media"
      else -> "Article"
    }
  }

  suspend fun generateAiSummary(
    content: String,
    metadata: UrlMetadata?,
    settings: AppSettings
  ): AiResult = withContext(Dispatchers.IO) {
    if (settings.apiKey.isBlank() && settings.apiProvider != "Free Public (Pollinations.ai)") {
      return@withContext AiResult(
        category = getLocalCategory(metadata?.url, content),
        summary = if (metadata != null) {
          metadata.description.ifBlank { "A link to ${metadata.host}" }
        } else {
          content.take(150) + if (content.length > 150) "..." else ""
        }
      )
    }

    try {
      val isCustom = settings.apiProvider == "Custom"
      val isFreePublic = settings.apiProvider == "Free Public (Pollinations.ai)"
      val isCompatible = isCustom || isFreePublic
      val modelName = if (isFreePublic) "openai" else settings.selectedModel
      val urlString = if (isCustom) {
        if (settings.apiBaseUrl.isBlank()) throw Exception("API Base URL missing")
        var base = settings.apiBaseUrl.removeSuffix("/")
        if (!base.endsWith("/chat/completions")) {
          base += "/chat/completions"
        }
        base
      } else if (isFreePublic) {
        "https://text.pollinations.ai/openai"
      } else {
        "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${settings.apiKey}"
      }

      val url = URL(urlString)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      connection.connectTimeout = 15000
      connection.readTimeout = 15000
      connection.setRequestProperty("Content-Type", "application/json")
      if (isCustom && settings.apiKey.isNotBlank()) {
        connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
      }
      connection.doOutput = true

      val prompt = buildString {
        append(settings.systemPrompt)
        append("\n\nInput content to process:\n")
        if (metadata != null) {
          append("Type: Link\n")
          append("URL: ${metadata.url}\n")
          append("Host: ${metadata.host}\n")
          append("Title: ${metadata.title}\n")
          append("Description: ${metadata.description}\n")
        } else {
          append("Type: Note\n")
          append("Content: $content\n")
        }
      }

      val requestPayload = if (isCompatible) {
        json.encodeToString(
          OpenAiRequest(
            model = modelName,
            messages = listOf(
              OpenAiMessage(role = "system", content = "You are a categorizing AI. Respond only in JSON."),
              OpenAiMessage(role = "user", content = prompt)
            ),
            response_format = OpenAiResponseFormat(type = "json_object")
          )
        )
      } else {
        json.encodeToString(
          GeminiRequest(
            contents = listOf(
              GeminiContent(
                parts = listOf(
                  GeminiPart(text = prompt)
                )
              )
            ),
            generationConfig = GeminiGenerationConfig(
              responseMimeType = "application/json"
            )
          )
        )
      }

      val writer = OutputStreamWriter(connection.outputStream)
      writer.write(requestPayload)
      writer.flush()
      writer.close()

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
        Log.e("NetworkHelper", "Gemini API Error: $responseCode - $errorStream")
        throw Exception("API call failed with code $responseCode")
      }

      val responseText = connection.inputStream.bufferedReader().use { it.readText() }
      connection.disconnect()

      val responseJson = json.parseToJsonElement(responseText)
      val textResponse = if (isCompatible) {
        responseJson.jsonObject["choices"]
          ?.jsonArray?.get(0)
          ?.jsonObject?.get("message")
          ?.jsonObject?.get("content")
          ?.jsonPrimitive?.content
          ?: throw Exception("Invalid OpenAI response structure")
      } else {
        responseJson.jsonObject["candidates"]
          ?.jsonArray?.get(0)
          ?.jsonObject?.get("content")
          ?.jsonObject?.get("parts")
          ?.jsonArray?.get(0)
          ?.jsonObject?.get("text")
          ?.jsonPrimitive?.content
          ?: throw Exception("Invalid Gemini response structure")
      }

      // Clean up response string in case the model outputs markdown backticks
      val cleanJsonText = textResponse.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

      val parsedResult = json.decodeFromString<AiResponseSchema>(cleanJsonText)

      AiResult(
        category = parsedResult.category,
        summary = parsedResult.summary
      )
    } catch (e: Throwable) {
      Log.e("NetworkHelper", "Error in generateAiSummary: ${e.message}", e)
      // Robust Fallback
      AiResult(
        category = getLocalCategory(metadata?.url, content),
        summary = if (metadata != null) {
          metadata.description.ifBlank { metadata.title.ifBlank { "A link to ${metadata.host}" } }
        } else {
          content.take(150) + if (content.length > 150) "..." else ""
        }
      )
    }
  }

  suspend fun chatWithAi(
    history: List<ChatMessage>,
    contextItems: List<SavedItem>,
    settings: AppSettings
  ): String = withContext(Dispatchers.IO) {
    if (settings.apiKey.isBlank() && settings.apiProvider == "Gemini") {
      return@withContext "Please configure an API key in settings."
    }
    
    val isCustom = settings.apiProvider == "Custom"
    val isFreePublic = settings.apiProvider == "Free Public (Pollinations.ai)"
    val isCompatible = isCustom || isFreePublic
    
    if (!isCompatible) {
      return@withContext geminiChatWithTools(history, contextItems, settings)
    }

    try {
      val modelName = if (isFreePublic) "openai" else settings.selectedModel
      val urlString = if (isCustom) {
        if (settings.apiBaseUrl.isBlank()) throw Exception("API Base URL missing")
        var base = settings.apiBaseUrl.removeSuffix("/")
        if (!base.endsWith("/chat/completions")) {
          base += "/chat/completions"
        }
        base
      } else {
        "https://text.pollinations.ai/openai"
      }

      val url = URL(urlString)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      connection.connectTimeout = 30000
      connection.readTimeout = 30000
      connection.setRequestProperty("Content-Type", "application/json")
      if (isCustom && settings.apiKey.isNotBlank()) {
        connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
      }
      connection.doOutput = true

      val systemPrompt = buildString {
        append(settings.chatSystemPrompt)
        append("\n\nCONTEXT:\n")
        contextItems.forEach { item ->
          append("- ID: ${item.id} | [${item.category}] ${item.title ?: item.content}: ${item.summary ?: item.description}\n")
        }
      }

      val openAiMessages = mutableListOf(
        OpenAiMessage(role = "system", content = systemPrompt)
      )
      history.forEach { msg ->
        openAiMessages.add(OpenAiMessage(role = msg.role, content = msg.content))
      }
      
      val requestPayload = json.encodeToString(
        OpenAiRequest(
          model = modelName,
          messages = openAiMessages
        )
      )

      val writer = OutputStreamWriter(connection.outputStream)
      writer.write(requestPayload)
      writer.flush()
      writer.close()

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
        Log.e("NetworkHelper", "API Error: $responseCode - $errorStream")
        return@withContext "API error ($responseCode): $errorStream"
      }

      val responseText = connection.inputStream.bufferedReader().use { it.readText() }
      connection.disconnect()

      val responseJson = json.parseToJsonElement(responseText)
      val textResponse = responseJson.jsonObject["choices"]
        ?.jsonArray?.get(0)
        ?.jsonObject?.get("message")
        ?.jsonObject?.get("content")
        ?.jsonPrimitive?.content
      
      textResponse ?: "Error: Could not parse response."
    } catch (e: Exception) {
      Log.e("NetworkHelper", "Chat error: ${e.message}", e)
      "Error: ${e.message}"
    }
  }

  private suspend fun geminiChatWithTools(
    history: List<ChatMessage>,
    contextItems: List<SavedItem>,
    settings: AppSettings,
    additionalHistory: List<GeminiContent> = emptyList(),
    depth: Int = 0
  ): String = withContext(Dispatchers.IO) {
    if (depth > 5) return@withContext "Error: Too many tool calls."

    try {
      val modelName = settings.selectedModel
      val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${settings.apiKey}"

      val url = URL(urlString)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      connection.connectTimeout = 30000
      connection.readTimeout = 30000
      connection.setRequestProperty("Content-Type", "application/json")
      connection.doOutput = true

      val systemPrompt = settings.chatSystemPrompt

      val geminiContents = mutableListOf<GeminiContent>()
      
      // Add standard history
      history.forEach { msg ->
        val role = if (msg.role == "assistant") "model" else "user"
        geminiContents.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.content))))
      }

      // Add tool call history
      geminiContents.addAll(additionalHistory)

      val tools = listOf(
        GeminiTool(
          functionDeclarations = listOf(
            GeminiFunctionDeclaration(
              name = "search_items",
              description = "Search the user's saved items using a keyword, topic, or category. Use this tool when the user asks a question that requires information from their saved data. Provide a search query.",
              parameters = GeminiSchema(
                type = "OBJECT",
                properties = mapOf(
                  "query" to GeminiSchemaProperty(
                    type = "STRING",
                    description = "The topic or keyword to search for in the user's items."
                  )
                ),
                required = listOf("query")
              )
            )
          )
        )
      )

      val requestPayload = json.encodeToString(
        GeminiRequest(
          systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
          contents = geminiContents,
          tools = tools
        )
      )

      val writer = OutputStreamWriter(connection.outputStream)
      writer.write(requestPayload)
      writer.flush()
      writer.close()

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
        Log.e("NetworkHelper", "API Error: $responseCode - $errorStream")
        return@withContext "API error ($responseCode): $errorStream"
      }

      val responseText = connection.inputStream.bufferedReader().use { it.readText() }
      connection.disconnect()

      val responseJson = json.parseToJsonElement(responseText)
      val part = responseJson.jsonObject["candidates"]
        ?.jsonArray?.get(0)
        ?.jsonObject?.get("content")
        ?.jsonObject?.get("parts")
        ?.jsonArray?.get(0)
        ?.jsonObject

      val textResponse = part?.get("text")?.jsonPrimitive?.content
      val functionCall = part?.get("functionCall")?.jsonObject

      if (functionCall != null) {
        val name = functionCall["name"]?.jsonPrimitive?.content
        val args = functionCall["args"]?.jsonObject

        if (name == "search_items") {
          val query = args?.get("query")?.jsonPrimitive?.content ?: ""
          
          val results = contextItems.filter { item ->
            val matchesQuery = query.isBlank() || 
                               item.content.contains(query, ignoreCase=true) || 
                               item.title?.contains(query, ignoreCase=true) == true || 
                               item.description?.contains(query, ignoreCase=true) == true ||
                               item.summary?.contains(query, ignoreCase=true) == true
            matchesQuery
          }
          
          val searchResultJson = buildJsonObject {
            put("results", buildJsonArray {
              results.forEach { item ->
                add(buildJsonObject {
                  put("id", item.id)
                  put("title", item.title ?: item.content)
                  put("category", item.category)
                  put("summary", item.summary ?: item.description ?: item.content.take(100))
                })
              }
            })
          }

          val newHistory = additionalHistory.toMutableList()
          // Add the model's function call request to history
          newHistory.add(
            GeminiContent(
              role = "model",
              parts = listOf(
                GeminiPart(
                  functionCall = GeminiFunctionCall(
                    name = "search_items",
                    args = args ?: buildJsonObject {}
                  )
                )
              )
            )
          )
          
          // Add the function response to history
          newHistory.add(
            GeminiContent(
              role = "user", // Function responses must have role "user"
              parts = listOf(
                GeminiPart(
                  functionResponse = GeminiFunctionResponse(
                    name = "search_items",
                    response = searchResultJson
                  )
                )
              )
            )
          )

          // Recursive call with new history
          return@withContext geminiChatWithTools(history, contextItems, settings, newHistory, depth + 1)
        }
        
        return@withContext "Called unknown tool: $name"
      } else {
        return@withContext textResponse ?: "Error: Could not parse response."
      }

    } catch (e: Exception) {
      Log.e("NetworkHelper", "Chat error: ${e.message}", e)
      return@withContext "Error: ${e.message}"
    }
  }
}
