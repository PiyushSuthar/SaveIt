package com.piyushsuthar.saveit.ui.share

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushsuthar.saveit.data.AiResult
import com.piyushsuthar.saveit.data.DataRepository
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.data.NetworkHelper
import com.piyushsuthar.saveit.data.SavedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────
// State
// ─────────────────────────────────────────────

sealed interface ShareSaveState {
  object Idle : ShareSaveState
  object Processing : ShareSaveState
  data class Done(val item: SavedItem) : ShareSaveState
  data class Error(val message: String) : ShareSaveState
}

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

class ShareViewModel(private val repository: DataRepository) : ViewModel() {

  private val _state = MutableStateFlow<ShareSaveState>(ShareSaveState.Idle)
  val state: StateFlow<ShareSaveState> = _state

  private val _thinkingStep = MutableStateFlow("Analysing…")
  val thinkingStep: StateFlow<String> = _thinkingStep

  fun save(sharedText: String, customTitle: String? = null) {
    if (sharedText.isBlank()) return
    viewModelScope.launch {
      _state.value = ShareSaveState.Processing
      try {
        val isLink = NetworkHelper.isUrl(sharedText)
        val id = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        val item = if (isLink) {
          _thinkingStep.value = "Fetching page metadata…"
          val metadata = NetworkHelper.fetchUrlMetadata(sharedText)
          val settings = repository.settings.value
          val aiResult = if (settings.autoSummarize) {
            _thinkingStep.value = "Generating AI summary…"
            NetworkHelper.generateAiSummary(sharedText, metadata, settings)
          } else {
            AiResult(
              category = NetworkHelper.getLocalCategory(metadata.url, sharedText),
              summary = metadata.description.ifBlank { "A link to ${metadata.host}" }
            )
          }
          SavedItem(
            id = id,
            content = metadata.url,
            title = customTitle?.ifBlank { null } ?: metadata.title.ifBlank { metadata.host },
            description = metadata.description,
            imageUrl = metadata.imageUrl,
            category = aiResult.category,
            createdAt = createdAt,
            summary = aiResult.summary
          )
        } else {
          _thinkingStep.value = "Categorising note…"
          val settings = repository.settings.value
          val aiResult = if (settings.autoSummarize) {
            _thinkingStep.value = "Generating AI summary…"
            NetworkHelper.generateAiSummary(sharedText, null, settings)
          } else {
            AiResult(category = "Note", summary = "")
          }
          SavedItem(
            id = id,
            content = sharedText,
            title = customTitle?.ifBlank { null }
              ?: if (sharedText.length > 60) sharedText.take(60) + "…" else sharedText,
            category = aiResult.category,
            createdAt = createdAt,
            summary = aiResult.summary
          )
        }

        _thinkingStep.value = "Saving to library…"
        repository.saveItem(item)
        _state.value = ShareSaveState.Done(item)
      } catch (e: Throwable) {
        _state.value = ShareSaveState.Error(e.message ?: "Something went wrong")
      }
    }
  }

  fun reset() {
    _state.value = ShareSaveState.Idle
  }
}

// ─────────────────────────────────────────────
// Share Receive Bottom Sheet
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiveSheet(
  sharedText: String,
  onDismiss: () -> Unit,
  viewModel: ShareViewModel = viewModel {
    ShareViewModel(DefaultDataRepository.getInstance())
  }
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val thinkingStep by viewModel.thinkingStep.collectAsStateWithLifecycle()

  val isLink = remember(sharedText) {
    sharedText.startsWith("http://") || sharedText.startsWith("https://")
  }
  val host = remember(sharedText) {
    if (isLink) try { android.net.Uri.parse(sharedText).host ?: "" } catch (e: Exception) { "" }
    else ""
  }

  var customTitle by remember { mutableStateOf("") }

  // Auto-save triggered immediately when sheet opens
  LaunchedEffect(sharedText) {
    if (state is ShareSaveState.Idle) {
      viewModel.save(sharedText)
    }
  }

  // Done: auto-dismiss after short delay
  LaunchedEffect(state) {
    if (state is ShareSaveState.Done) {
      kotlinx.coroutines.delay(1800)
      onDismiss()
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = { BottomSheetDefaults.DragHandle() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

      // Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isLink) Icons.Default.Link else Icons.Default.Notes,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
          )
        }
        Column {
          Text(
            text = "Save to SaveIt",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = if (isLink) host else "Text note",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Shared content preview
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = sharedText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          fontStyle = if (isLink) FontStyle.Italic else FontStyle.Normal,
          modifier = Modifier.padding(12.dp)
        )
      }

      // State-based UI
      AnimatedContent(
        targetState = state,
        transitionSpec = {
          fadeIn(tween(300)) togetherWith fadeOut(tween(200))
        },
        label = "ShareStateAnim"
      ) { currentState ->
        when (currentState) {
          is ShareSaveState.Idle -> {
            // Nothing yet
            Box(modifier = Modifier.height(1.dp))
          }

          is ShareSaveState.Processing -> {
            ProcessingIndicator(step = thinkingStep)
          }

          is ShareSaveState.Done -> {
            SuccessView(item = currentState.item)
          }

          is ShareSaveState.Error -> {
            ErrorView(
              message = currentState.message,
              onRetry = {
                viewModel.reset()
                viewModel.save(sharedText, customTitle.ifBlank { null })
              }
            )
          }
        }
      }

      // Dismiss button (always visible)
      if (state !is ShareSaveState.Done) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
          Text("Cancel")
        }
      }
    }
  }
}

// ─────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────

@Composable
private fun ProcessingIndicator(step: String) {
  val infiniteTransition = rememberInfiniteTransition(label = "ProcessDots")
  val dotAlpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
    label = "DotAlpha"
  )

  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer
    ),
    shape = RoundedCornerShape(16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Animated dot trio
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
          val delay = i * 200
          val dotAnim by rememberInfiniteTransition(label = "Dot$i").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
              tween(600, delayMillis = delay),
              RepeatMode.Reverse
            ),
            label = "Dot${i}Alpha"
          )
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = dotAnim)
              )
          )
        }
      }
      Text(
        text = step,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.weight(1f)
      )
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
  }
}

@Composable
private fun SuccessView(item: SavedItem) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    ),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          Icons.Default.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          "Saved successfully",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onTertiaryContainer
        )
      }

      // Saved item preview
      if (!item.title.isNullOrBlank()) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      // Category chip
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .clip(RoundedCornerShape(50))
          .background(MaterialTheme.colorScheme.tertiaryContainer)
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Text(
          text = item.category,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        if (!item.summary.isNullOrBlank()) {
          Text(
            "· AI summary added",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
          )
        }
      }
    }
  }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    ),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          Icons.Default.ErrorOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp)
        )
        Text(
          "Could not save",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
      )
      FilledTonalButton(
        onClick = onRetry,
        shape = RoundedCornerShape(50),
        modifier = Modifier.align(Alignment.End)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Retry")
      }
    }
  }
}
