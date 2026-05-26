package com.piyushsuthar.saveit.ui.group

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushsuthar.saveit.data.DataRepository
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.data.ItemGroup
import com.piyushsuthar.saveit.data.SavedItem
import com.piyushsuthar.saveit.ui.main.SavedItemRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

sealed class GroupDetailUiState {
    data object Loading : GroupDetailUiState()
    data class Success(
        val group: ItemGroup,
        val items: List<SavedItem>
    ) : GroupDetailUiState()
    data object Error : GroupDetailUiState()
}

class GroupDetailViewModel(
    private val repository: DataRepository,
    private val groupId: String
) : ViewModel() {

    val uiState: StateFlow<GroupDetailUiState> = combine(
        repository.itemGroups,
        repository.savedItems
    ) { groups, savedItems ->
        val group = groups.find { it.id == groupId }
        if (group == null) {
            GroupDetailUiState.Error
        } else {
            val groupItems = savedItems.filter { it.groupId == groupId }
                .sortedByDescending { it.createdAt }
            GroupDetailUiState.Success(group, groupItems)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GroupDetailUiState.Loading
    )

    fun ungroupItem(itemId: String) {
        viewModelScope.launch {
            val itemToUpdate = repository.savedItems.value.find { it.id == itemId }
            if (itemToUpdate != null) {
                repository.saveItem(itemToUpdate.copy(groupId = null))
            }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            val itemsToUpdate = repository.savedItems.value
                .filter { it.groupId == groupId }
                .map { it.copy(groupId = null) }
            repository.saveItems(itemsToUpdate)
            repository.deleteItemGroup(groupId)
        }
    }
}

class GroupDetailViewModelFactory(
    private val repository: DataRepository,
    private val groupId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GroupDetailViewModel(repository, groupId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = DefaultDataRepository.getInstance()
    val viewModel: GroupDetailViewModel = viewModel(factory = GroupDetailViewModelFactory(repository, groupId))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state is GroupDetailUiState.Success) {
                            (state as GroupDetailUiState.Success).group.name
                        } else {
                            "Group"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is GroupDetailUiState.Success) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Group")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val s = state) {
                GroupDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                GroupDetailUiState.Error -> {
                    Text(
                        text = "Group not found or deleted.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is GroupDetailUiState.Success -> {
                    if (s.items.isEmpty()) {
                        Text(
                            text = "No items in this group.",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(s.items, key = { it.id }) { item ->
                                // Note: we could add a swipe to delete or context menu to ungroup
                                // But for now let's just show it using SavedItemRow
                                SavedItemRow(
                                    item = item,
                                    isSelected = false,
                                    onItemClick = { onItemClick(item.id) },
                                    onItemLongClick = { viewModel.ungroupItem(item.id) },
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Group?") },
            text = { Text("This will delete the group, but the items inside will remain in your feed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup()
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
}
