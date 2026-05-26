package com.piyushsuthar.saveit.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushsuthar.saveit.data.DataRepository
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.data.SavedItem
import com.piyushsuthar.saveit.ui.main.getCategoryIcon
import com.piyushsuthar.saveit.ui.main.formatTimeElapsed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteDetailViewModel(
  private val itemId: String,
  private val repository: DataRepository
) : ViewModel() {

  val itemState: StateFlow<SavedItem?> = repository.savedItems
    .map { list -> list.find { it.id == itemId } }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null
    )

  fun deleteItem() {
    viewModelScope.launch {
      repository.deleteItem(itemId)
    }
  }

  fun updateItem(title: String, content: String, summary: String?) {
    val current = itemState.value ?: return
    viewModelScope.launch {
      repository.saveItem(
        current.copy(
          title = title,
          content = content,
          summary = summary
        )
      )
    }
  }

  fun updateCategory(category: String) {
    val current = itemState.value ?: return
    viewModelScope.launch {
      repository.saveItem(
        current.copy(category = category)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
  itemId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: NoteDetailViewModel = viewModel(key = itemId) { 
    NoteDetailViewModel(itemId, DefaultDataRepository.getInstance()) 
  }
) {
  val context = LocalContext.current
  val item by viewModel.itemState.collectAsStateWithLifecycle()
  
  var editMode by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }
  
  // Form values
  var editTitle by remember { mutableStateOf("") }
  var editContent by remember { mutableStateOf("") }
  var editSummary by remember { mutableStateOf("") }

  LaunchedEffect(item) {
    item?.let {
      editTitle = it.title ?: ""
      editContent = it.content
      editSummary = it.summary ?: ""
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.Filled.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        },
        actions = {
          if (item != null) {
            // Edit / Save toggle
            IconButton(
              onClick = { 
                if (editMode) {
                  viewModel.updateItem(
                    title = editTitle.trim(),
                    content = editContent.trim(),
                    summary = editSummary.trim().ifBlank { null }
                  )
                  editMode = false
                  Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                } else {
                  editMode = true
                }
              }
            ) {
              Icon(
                imageVector = if (editMode) Icons.Filled.Check else Icons.Outlined.Edit,
                contentDescription = if (editMode) "Save" else "Edit",
                tint = if (editMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            
            // Share button
            IconButton(
              onClick = {
                val sendIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_TEXT, item?.content ?: "")
                  type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
              }
            ) {
              Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            
            // More menu
            Box {
              IconButton(onClick = { showMoreMenu = true }) {
                Icon(
                  imageVector = Icons.Filled.MoreVert,
                  contentDescription = "More options",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
              ) {
                val currentItem = item!!
                val isLink = currentItem.content.startsWith("http://") || currentItem.content.startsWith("https://")

                DropdownMenuItem(
                  text = { Text("Copy") },
                  leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("SaveIt", currentItem.content)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    showMoreMenu = false
                  }
                )

                // (Share moved to visible button above)

                if (isLink) {
                  DropdownMenuItem(
                    text = { Text("Open in browser") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                    onClick = {
                      try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.content)))
                      } catch (e: Exception) {
                        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                      }
                      showMoreMenu = false
                    }
                  )
                }

                HorizontalDivider()

                DropdownMenuItem(
                  text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                  leadingIcon = { 
                    Icon(
                      Icons.Outlined.Delete, 
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error
                    ) 
                  },
                  onClick = {
                    showMoreMenu = false
                    showDeleteDialog = true
                  }
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    modifier = modifier.fillMaxSize()
  ) { innerPadding ->
    if (item == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
          )
          Text(
            "Item not found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      val currentItem = item!!
      val isLink = currentItem.content.startsWith("http://") || currentItem.content.startsWith("https://")

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // ── Category & time chip row ──
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = getCategoryIcon(currentItem.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = currentItem.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
              )
            }
          }

          Text(
            text = formatTimeElapsed(currentItem.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // ── Title ──
        AnimatedContent(
          targetState = editMode,
          transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
          },
          label = "TitleEdit"
        ) { editing ->
          if (editing) {
            OutlinedTextField(
              value = editTitle,
              onValueChange = { editTitle = it },
              placeholder = { Text("Title") },
              textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
              ),
              modifier = Modifier.fillMaxWidth()
            )
          } else {
            Text(
              text = currentItem.title ?: currentItem.content,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        // ── Hostname (links) ──
        if (isLink && !editMode) {
          val hostName = try { Uri.parse(currentItem.content).host ?: "" } catch (e: Exception) { "" }
          if (hostName.isNotEmpty()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.clickable {
                try {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.content)))
                } catch (e: Exception) {
                  Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                }
              }
            ) {
              Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = hostName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
              )
              Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }

        // ── Thumbnail Image ──
        if (!currentItem.imageUrl.isNullOrBlank() && !editMode) {
          AsyncImage(
            model = currentItem.imageUrl,
            contentDescription = "Thumbnail for ${currentItem.title ?: "link"}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 240.dp)
              .clip(RoundedCornerShape(12.dp))
          )
        }

        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          thickness = 0.5.dp
        )

        // ── AI Summary ──
        if (!currentItem.summary.isNullOrBlank() || editMode) {
          AnimatedContent(
            targetState = editMode,
            transitionSpec = {
              fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "SummaryEdit"
          ) { editing ->
            if (editing) {
              OutlinedTextField(
                value = editSummary,
                onValueChange = { editSummary = it },
                label = { Text("AI Summary") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 80.dp, max = 150.dp)
              )
            } else {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.AutoAwesome,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      "AI Summary",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  
                  Text(
                    text = currentItem.summary ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 22.sp
                  )
                }
              }
            }
          }
        }

        // ── Content ──
        AnimatedContent(
          targetState = editMode,
          transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
          },
          label = "ContentEdit"
        ) { editing ->
          if (editing) {
            OutlinedTextField(
              value = editContent,
              onValueChange = { editContent = it },
              label = { Text("Content") },
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
              ),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 300.dp)
            )
          } else {
            Text(
              text = currentItem.content,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 24.sp,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        // ── Quick action buttons (view mode only, for links) ──
        if (!editMode && isLink) {
          Spacer(modifier = Modifier.height(8.dp))
          
          FilledTonalButton(
            onClick = {
              try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.content)))
              } catch (e: Exception) {
                Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.OpenInBrowser,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open in browser", fontWeight = FontWeight.Medium)
          }
        }

        // Bottom spacer
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  // Delete Dialog
  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      icon = {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
      },
      title = { Text("Delete this item?") },
      text = { Text("This will permanently remove this item. This action cannot be undone.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteItem()
            showDeleteDialog = false
            onBack()
            Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
}
