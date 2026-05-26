package com.piyushsuthar.saveit.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.ui.main.formatTimeElapsed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
  onBack: () -> Unit,
  onNavigateToChat: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ChatViewModel = viewModel { ChatViewModel(DefaultDataRepository.getInstance()) }
) {
  val threads by viewModel.chatThreads.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Chats") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          val newThreadId = viewModel.createNewThread()
          onNavigateToChat(newThreadId)
        }
      ) {
        Icon(Icons.Default.Add, contentDescription = "New Chat")
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    var threadToDelete by remember { mutableStateOf<String?>(null) }

    if (threads.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            "No chats yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
      ) {
        items(threads, key = { it.id }) { thread ->
          ListItem(
            headlineContent = { Text(thread.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
              if (thread.messages.isNotEmpty()) {
                Text(
                  thread.messages.last().content,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              } else {
                Text("Empty chat")
              }
            },
            trailingContent = {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  formatTimeElapsed(thread.createdAt),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                  onClick = { threadToDelete = thread.id },
                  modifier = Modifier.size(32.dp).padding(top = 4.dp)
                ) {
                  Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Chat",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            },
            modifier = Modifier.clickable {
              onNavigateToChat(thread.id)
            }
          )
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
      }
    }

    if (threadToDelete != null) {
      AlertDialog(
        onDismissRequest = { threadToDelete = null },
        title = { Text("Delete Chat?") },
        text = { Text("Are you sure you want to delete this chat thread?") },
        confirmButton = {
          Button(
            onClick = {
              threadToDelete?.let { viewModel.deleteThread(it) }
              threadToDelete = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("Delete")
          }
        },
        dismissButton = {
          TextButton(onClick = { threadToDelete = null }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}
