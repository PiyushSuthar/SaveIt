package com.piyushsuthar.saveit.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushsuthar.saveit.data.ChatMessage
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.ui.main.SavedItemRow
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  threadId: String,
  onBack: () -> Unit,
  onNavigateToDetail: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ChatViewModel = viewModel { ChatViewModel(DefaultDataRepository.getInstance()) }
) {
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
  val threads by viewModel.chatThreads.collectAsStateWithLifecycle()
  var inputText by remember { mutableStateOf(TextFieldValue("")) }
  val listState = rememberLazyListState()

  LaunchedEffect(threadId) {
    viewModel.selectThread(threadId)
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Memory Assistant") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          var expanded by remember { mutableStateOf(false) }
          var showDeleteDialog by remember { mutableStateOf(false) }
          
          IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
          }
          
          DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
              onClick = {
                expanded = false
                showDeleteDialog = true
              }
            )
          }
          
          if (showDeleteDialog) {
            AlertDialog(
              onDismissRequest = { showDeleteDialog = false },
              title = { Text("Delete Chat?") },
              text = { Text("Are you sure you want to delete this chat thread?") },
              confirmButton = {
                Button(
                  onClick = {
                    viewModel.deleteThread(threadId)
                    showDeleteDialog = false
                    onBack()
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                  Text("Delete")
                }
              },
              dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                  Text("Cancel")
                }
              }
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()
    ) {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        items(messages) { msg ->
          ChatBubble(message = msg, onNavigateToDetail = onNavigateToDetail)
        }
        if (isProcessing) {
          item {
            ChatBubble(message = ChatMessage("assistant", "Thinking..."), isTyping = true, onNavigateToDetail = {})
          }
        }
      }

      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("Ask about your links...") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
          )
          Spacer(modifier = Modifier.width(8.dp))
          FilledIconButton(
            onClick = {
              if (inputText.text.isNotBlank()) {
                viewModel.sendMessage(inputText.text)
                inputText = TextFieldValue("")
              }
            },
            enabled = inputText.text.isNotBlank() && !isProcessing,
            shape = RoundedCornerShape(24.dp)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
          }
        }
      }
    }
  }
}

@Composable
fun ChatBubble(message: ChatMessage, isTyping: Boolean = false, onNavigateToDetail: (String) -> Unit) {
  val isUser = message.role == "user"
  val backgroundColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
  val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
  val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
  val shape = if (isUser) {
    RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
  } else {
    RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
  }

  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
    Surface(
      color = backgroundColor,
      shape = shape,
      modifier = Modifier.widthIn(max = 320.dp)
    ) {
      if (isTyping) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Text("Thinking...", style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
      } else {
        Column {
          Box(modifier = Modifier.padding(16.dp)) {
             // If markdown fails to compile we will switch to Text, but MarkdownText is correct for dev.jeziellago
             MarkdownText(
               markdown = message.content,
               color = textColor,
               style = MaterialTheme.typography.bodyMedium
             )
          }
          if (message.embeddedItems.isNotEmpty()) {
            LazyRow(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
              items(message.embeddedItems) { item ->
                Box(
                  modifier = Modifier
                    .width(280.dp)
                    .clickable { onNavigateToDetail(item.id) }
                ) {
                  SavedItemRow(
                    item = item,
                    isSelected = false,
                    onItemClick = { onNavigateToDetail(item.id) },
                    onItemLongClick = {}
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
