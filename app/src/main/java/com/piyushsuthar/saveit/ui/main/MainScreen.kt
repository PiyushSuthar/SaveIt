package com.piyushsuthar.saveit.ui.main

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.piyushsuthar.saveit.ChatList
import com.piyushsuthar.saveit.GroupDetail
import com.piyushsuthar.saveit.NoteDetail
import com.piyushsuthar.saveit.Settings
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.data.SavedItem
import kotlinx.coroutines.launch

// Shared utility
fun formatTimeElapsed(timestamp: Long): String {
  val elapsed = System.currentTimeMillis() - timestamp
  if (elapsed < 0) return "Just now"
  val seconds = elapsed / 1000
  if (seconds < 60) return "Just now"
  val minutes = seconds / 60
  if (minutes < 60) return "${minutes}m ago"
  val hours = minutes / 60
  if (hours < 24) return "${hours}h ago"
  val days = hours / 24
  if (days < 7) return "${days}d ago"
  val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
  return sdf.format(java.util.Date(timestamp))
}

// Category icon mapping
fun getCategoryIcon(category: String): ImageVector {
  return when (category) {
    "Article" -> Icons.Outlined.Article
    "Video" -> Icons.Outlined.PlayCircle
    "Social Media" -> Icons.Outlined.Forum
    "Shopping" -> Icons.Outlined.ShoppingBag
    "Code" -> Icons.Outlined.Code
    "Note" -> Icons.Outlined.StickyNote2
    else -> Icons.Outlined.Folder
  }
}

fun Modifier.bounce(): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository.getInstance()) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Box(modifier = modifier.fillMaxSize()) {
    when (state) {
      MainScreenUiState.Loading -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
      is MainScreenUiState.Success -> {
        val successState = state as MainScreenUiState.Success
        MainContent(
          successState = successState,
          onSettingsClick = { onItemClick(Settings) },
          onChatClick = { onItemClick(ChatList) },
          onItemClick = { itemId -> onItemClick(NoteDetail(itemId)) },
          onGroupClick = { groupId -> onItemClick(GroupDetail(groupId)) },
          onSearchQueryChange = { viewModel.updateSearchQuery(it) },
          onCategorySelected = { viewModel.selectCategory(it) },
          onSubmitInput = { viewModel.submitInput(it) },
          onDeleteItem = { viewModel.deleteItem(it) },
          onRestoreItem = { viewModel.restoreItem(it) },
          onToggleSelection = { viewModel.toggleSelection(it) },
          onClearSelection = { viewModel.clearSelection() },
          onDeleteSelected = { viewModel.deleteSelectedItems() },
          onGroupSelected = { name -> viewModel.groupSelectedItems(name) },
          onAddToGroup = { id -> viewModel.addSelectedItemsToGroup(id) },
          onSuggestGroupName = { viewModel.suggestGroupNameForSelected(successState.settings) }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainContent(
  successState: MainScreenUiState.Success,
  onSettingsClick: () -> Unit,
  onChatClick: () -> Unit,
  onItemClick: (String) -> Unit,
  onGroupClick: (String) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onCategorySelected: (String) -> Unit,
  onSubmitInput: (String) -> Unit,
  onDeleteItem: (String) -> Unit,
  onRestoreItem: (SavedItem) -> Unit,
  onToggleSelection: (String) -> Unit,
  onClearSelection: () -> Unit,
  onDeleteSelected: () -> Unit,
  onGroupSelected: (String) -> Unit,
  onAddToGroup: (String) -> Unit,
  onSuggestGroupName: suspend () -> String
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var inputText by remember { mutableStateOf("") }
  var isSearchActive by remember { mutableStateOf(false) }
  var showGroupDialog by remember { mutableStateOf(false) }
  var groupNameInput by remember { mutableStateOf("") }
  var isSuggestingName by remember { mutableStateOf(false) }

  val categories = listOf(
    "All", "Note", "Article", "Video", "Social Media", "Shopping", "Code", "Other"
  )

  val allItems = remember(successState.savedItems, successState.itemGroups) {
    val items = successState.savedItems.sortedByDescending { it.createdAt }
    val grouped = mutableListOf<FeedItem>()
    val processedGroupIds = mutableSetOf<String>()
    
    for (item in items) {
        if (item.groupId != null) {
            if (processedGroupIds.add(item.groupId)) {
                val group = successState.itemGroups.find { it.id == item.groupId }
                if (group != null) {
                    val count = successState.savedItems.count { it.groupId == item.groupId }
                    grouped.add(FeedItem.Group(group, count))
                } else {
                    grouped.add(FeedItem.Item(item))
                }
            }
        } else {
            grouped.add(FeedItem.Item(item))
        }
    }
    grouped
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
  ) { scaffoldPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(scaffoldPadding)
    ) {
      LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        // ── Top Bar ──
        item {
          AnimatedContent(
            targetState = successState.selectedItems.isNotEmpty(),
            label = "topBar"
          ) { hasSelection ->
            if (hasSelection) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = onClearSelection, modifier = Modifier.bounce()) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Clear selection",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "${successState.selectedItems.size} Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
                Row {
                  IconButton(onClick = { showGroupDialog = true }, modifier = Modifier.bounce()) {
                    Icon(
                      imageVector = Icons.Outlined.CreateNewFolder,
                      contentDescription = "Group selected",
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                  IconButton(onClick = onDeleteSelected, modifier = Modifier.bounce()) {
                    Icon(
                      imageVector = Icons.Outlined.Delete,
                      contentDescription = "Delete selected",
                      tint = MaterialTheme.colorScheme.error
                    )
                  }
                }
              }
            } else {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "SaveIt",
                  style = MaterialTheme.typography.headlineMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
    
                Row(
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  IconButton(onClick = { isSearchActive = !isSearchActive }, modifier = Modifier.bounce()) {
                    Icon(
                      imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Outlined.Search,
                      contentDescription = "Search",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  IconButton(onClick = onChatClick, modifier = Modifier.bounce()) {
                    Icon(
                      imageVector = Icons.Outlined.AutoAwesome,
                      contentDescription = "Chat with AI",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  IconButton(onClick = onSettingsClick, modifier = Modifier.bounce()) {
                    Icon(
                      imageVector = Icons.Outlined.Settings,
                      contentDescription = "Settings",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }

        // ── Search Bar ──
        item {
          AnimatedVisibility(
            visible = isSearchActive || successState.searchQuery.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
          ) {
            OutlinedTextField(
              value = successState.searchQuery,
              onValueChange = onSearchQueryChange,
              placeholder = {
                Text(
                  "Search your saves...",
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              trailingIcon = {
                if (successState.searchQuery.isNotEmpty()) {
                  IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Clear search",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              },
              shape = RoundedCornerShape(28.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
              ),
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
            )
          }
        }

        // ── Category Filter Chips ──
        item {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(categories) { category ->
              val isSelected = successState.selectedCategory == category
              val count = successState.categoryCounts[category] ?: 0

              FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.bounce(),
                label = {
                  Text(
                    text = if (category == "All") "All ($count)" else "$category ($count)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                  )
                },
                leadingIcon = if (isSelected) {
                  {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                } else null,
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                  borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                  selectedBorderColor = Color.Transparent,
                  enabled = true,
                  selected = isSelected
                )
              )
            }
          }
        }

        // ── Processing Indicator ──
        item {
          AnimatedVisibility(
            visible = successState.isProcessing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
          ) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
              tonalElevation = 0.dp
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Processing...",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                  val lastStep = successState.thinkingSteps.lastOrNull()
                  if (lastStep != null) {
                    Text(
                      text = lastStep,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
            }
          }
        }

        // ── Content ──

        if (successState.searchQuery.isNotEmpty()) {
          // Search results header
          item {
            Text(
              text = "${successState.savedItems.size} results",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
          }

          if (successState.savedItems.isEmpty()) {
            item {
              EmptySearchView()
            }
          } else {
            items(successState.savedItems, key = { it.id }) { item ->
              // For search results, we just show all items as flat list
              SavedItemRow(
                item = item,
                isSelected = successState.selectedItems.contains(item.id),
                onItemClick = {
                  if (successState.selectedItems.isNotEmpty()) {
                    onToggleSelection(item.id)
                  } else {
                    onItemClick(item.id)
                  }
                },
                onItemLongClick = { onToggleSelection(item.id) },
                modifier = Modifier.padding(horizontal = 20.dp)
              )
            }
          }
        } else if (allItems.isEmpty()) {
          item {
            EmptyStateView()
          }
        } else {
          // Section header
          item {
            Text(
              text = if (successState.selectedCategory == "All") "Recent" else successState.selectedCategory,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
          }

          items(allItems, key = { it.id }) { feedItem ->
            when (feedItem) {
                is FeedItem.Item -> {
                    SavedItemRow(
                      item = feedItem.savedItem,
                      isSelected = successState.selectedItems.contains(feedItem.savedItem.id),
                      onItemClick = {
                        if (successState.selectedItems.isNotEmpty()) {
                          onToggleSelection(feedItem.savedItem.id)
                        } else {
                          onItemClick(feedItem.savedItem.id)
                        }
                      },
                      onItemLongClick = { onToggleSelection(feedItem.savedItem.id) },
                      modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                is FeedItem.Group -> {
                    GroupRow(
                      group = feedItem.itemGroup,
                      itemCount = feedItem.count,
                      onGroupClick = {
                          if (successState.selectedItems.isNotEmpty()) {
                              // Maybe ignore clicks when selecting, or just open group
                              onGroupClick(feedItem.itemGroup.id)
                          } else {
                              onGroupClick(feedItem.itemGroup.id)
                          }
                      },
                      modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
          }
        }
      }

      // ── Floating Input Bar ──
      Surface(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IconButton(
            onClick = {
              val clipText = clipboardManager.getText()?.text
              if (!clipText.isNullOrBlank()) {
                inputText = clipText
                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.bounce()
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentPaste,
              contentDescription = "Paste from clipboard",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          BasicTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { innerTextField ->
              if (inputText.isEmpty()) {
                Text(
                  text = "Paste a link or write a note...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
              }
              innerTextField()
            },
            singleLine = true
          )

          FilledIconButton(
            onClick = {
              if (inputText.isNotBlank()) {
                onSubmitInput(inputText.trim())
                inputText = ""
              }
            },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.size(40.dp).bounce()
          ) {
            Icon(
              imageVector = Icons.Filled.ArrowForward,
              contentDescription = "Open Chat",
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }

  if (showGroupDialog) {
    AlertDialog(
      onDismissRequest = { showGroupDialog = false },
      title = { Text("Group Items") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          if (successState.itemGroups.isNotEmpty()) {
            Text("Add to existing group:", fontWeight = FontWeight.SemiBold)
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(successState.itemGroups, key = { it.id }) { group ->
                Surface(
                  onClick = {
                    onAddToGroup(group.id)
                    showGroupDialog = false
                  },
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(group.name, modifier = Modifier.padding(12.dp))
                }
              }
            }
            HorizontalDivider()
            Text("Or create a new group:", fontWeight = FontWeight.SemiBold)
          } else {
            Text("Enter a name for this group of items:")
          }

          OutlinedTextField(
            value = groupNameInput,
            onValueChange = { groupNameInput = it },
            label = { Text("Group Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Button(
            onClick = {
              scope.launch {
                isSuggestingName = true
                groupNameInput = onSuggestGroupName()
                isSuggestingName = false
              }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            enabled = !isSuggestingName
          ) {
            if (isSuggestingName) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Suggesting...")
            } else {
              Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Suggest Name with AI")
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (groupNameInput.isNotBlank()) {
              onGroupSelected(groupNameInput)
              showGroupDialog = false
              groupNameInput = ""
            }
          }
        ) {
          Text("Create Group")
        }
      },
      dismissButton = {
        TextButton(onClick = { showGroupDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

// ── Saved Item Row ──

private sealed class FeedItem {
    data class Group(val itemGroup: com.piyushsuthar.saveit.data.ItemGroup, val count: Int) : FeedItem()
    data class Item(val savedItem: SavedItem) : FeedItem()
    
    val id: String
        get() = when (this) {
            is Group -> itemGroup.id
            is Item -> savedItem.id
        }
}

@Composable
fun GroupRow(
    group: com.piyushsuthar.saveit.data.ItemGroup,
    itemCount: Int,
    onGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onGroupClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$itemCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = "View Group",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedItemRow(
  item: SavedItem,
  isSelected: Boolean,
  onItemClick: () -> Unit,
  onItemLongClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isLink = item.content.startsWith("http://") || item.content.startsWith("https://")
  val backgroundColor by animateColorAsState(
    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
    label = "selectionColor"
  )
  val scale by animateFloatAsState(
    targetValue = if (isSelected) 0.98f else 1f,
    label = "selectionScale"
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      },
    shape = RoundedCornerShape(16.dp),
    color = backgroundColor
  ) {
    Row(
      modifier = Modifier
        .combinedClickable(
          onClick = onItemClick,
          onLongClick = onItemLongClick
        )
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.Top
    ) {
      // Category icon
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp)
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = getCategoryIcon(item.category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      // Content
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        // Title
        Text(
          text = item.title ?: item.content,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        // Subtitle: hostname or description
        val subtitle = remember(item.content, isLink) {
          if (isLink) {
            try { android.net.Uri.parse(item.content).host ?: "" } catch (e: Exception) { "" }
          } else {
            item.description ?: item.content
          }
        }
        if (subtitle.isNotEmpty() && subtitle != (item.title ?: "")) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        // Summary if available
        if (!item.summary.isNullOrBlank()) {
          Text(
            text = item.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontStyle = FontStyle.Italic
          )
        }

        // Meta row
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 4.dp)
        ) {
          Text(
            text = item.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          )
          Text(
            text = formatTimeElapsed(item.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
          )
          if (!item.summary.isNullOrBlank()) {
            Text(
              text = "·",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Icon(
              imageVector = Icons.Outlined.AutoAwesome,
              contentDescription = "AI summarized",
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }
    }
  }

  HorizontalDivider(
    modifier = modifier.padding(start = 62.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    thickness = 0.5.dp
  )
}

// ── Empty State ──
@Composable
fun EmptyStateView(
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 40.dp, vertical = 80.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
      modifier = Modifier.size(72.dp)
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.Outlined.BookmarkAdd,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = "Nothing saved yet",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface
    )

    Text(
      text = "Paste a link or type a note in the bar below\nto start saving.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      lineHeight = 22.sp
    )
  }
}

// ── Empty Search ──
@Composable
fun EmptySearchView(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 60.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = Icons.Outlined.SearchOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(40.dp)
      )
      Text(
        "No results found",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
      )
    }
  }
}
