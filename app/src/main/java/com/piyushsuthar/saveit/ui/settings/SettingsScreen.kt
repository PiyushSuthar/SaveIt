package com.piyushsuthar.saveit.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piyushsuthar.saveit.data.AppSettings
import com.piyushsuthar.saveit.data.DataRepository
import com.piyushsuthar.saveit.data.DefaultDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: DataRepository) : ViewModel() {
  val settings: StateFlow<AppSettings> = repository.settings.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = AppSettings()
  )

  fun updateSettings(newSettings: AppSettings) {
    viewModelScope.launch { repository.updateSettings(newSettings) }
  }

  fun clearAllData() {
    viewModelScope.launch { repository.clearAllData() }
  }

  suspend fun importBackup(json: String): Boolean = repository.importBackup(json)

  fun exportBackup(): String = repository.exportBackup()
}

// ─────────────────────────────────────────────────────────────
// Main Settings Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onNavigateToAi: () -> Unit,
  onNavigateToData: () -> Unit,
  onNavigateToDanger: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    topBar = {
      LargeTopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
    ) {
      SettingsMenuRow(
        title = "AI Integration",
        description = "Configure AI provider, keys, and prompts",
        icon = Icons.Default.SmartToy,
        onClick = onNavigateToAi
      )
      
      SettingsMenuRow(
        title = "Data & Backups",
        description = "Export and import your saved data",
        icon = Icons.Default.Storage,
        onClick = onNavigateToData
      )
      
      SettingsMenuRow(
        title = "Danger Zone",
        description = "Clear all app data",
        icon = Icons.Default.Warning,
        onClick = onNavigateToDanger
      )
      
      Spacer(modifier = Modifier.height(32.dp))
      Text(
        text = "SaveIt · v1.0",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
      )
      Text(
        text = "Local-first, privacy-focused",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
      )
    }
  }
}

@Composable
fun SettingsMenuRow(
  title: String,
  description: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    color = androidx.compose.ui.graphics.Color.Transparent
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 16.dp)
        .fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.size(48.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }
      
      Spacer(modifier = Modifier.width(16.dp))
      
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      
      Icon(
        imageVector = Icons.Default.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAiScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel { SettingsViewModel(DefaultDataRepository.getInstance()) }
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("AI Integration") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
    ) {
      SettingsToggleRow(
        title = "Auto-Summarize",
        description = "Categorize and summarize saved items with Gemini AI",
        checked = settings.autoSummarize,
        onCheckedChange = { viewModel.updateSettings(settings.copy(autoSummarize = it)) }
      )

      AnimatedVisibility(
        visible = settings.autoSummarize,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Column {
          AiConfigurationSection(
            settings = settings,
            onSettingsChange = { viewModel.updateSettings(it) }
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel { SettingsViewModel(DefaultDataRepository.getInstance()) }
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showImportDialog by remember { mutableStateOf(false) }
  
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Data & Backups") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        FilledTonalButton(
          onClick = {
            val backup = viewModel.exportBackup()
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("SaveIt Backup", backup))
            Toast.makeText(context, "Backup copied to clipboard", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(28.dp)
        ) {
          Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Export")
        }

        FilledTonalButton(
          onClick = { showImportDialog = true },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(28.dp)
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Import")
        }
      }
    }
  }
  
  if (showImportDialog) {
    ImportBackupDialog(
      onDismiss = { showImportDialog = false },
      onImport = { json ->
        scope.launch {
          val ok = viewModel.importBackup(json)
          if (ok) {
            Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
            showImportDialog = false
          } else {
            Toast.makeText(context, "Invalid backup format", Toast.LENGTH_LONG).show()
          }
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDangerScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel { SettingsViewModel(DefaultDataRepository.getInstance()) }
) {
  val context = LocalContext.current
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Danger Zone") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
    ) {
      OutlinedButton(
        onClick = { showClearDialog = true },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
      ) {
        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Clear All Data")
      }
    }
  }
  
  if (showClearDialog) {
    ClearDataDialog(
      onDismiss = { showClearDialog = false },
      onConfirm = {
        viewModel.clearAllData()
        showClearDialog = false
        Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
      }
    )
  }
}

// ─────────────────────────────────────────────────────────────
// AI Configuration Section (shown when auto-summarize is ON)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiConfigurationSection(
  settings: AppSettings,
  onSettingsChange: (AppSettings) -> Unit
) {
  var apiKeyText by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
  var apiKeyVisible by remember { mutableStateOf(false) }
  var promptText by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
  var chatPromptText by remember(settings.chatSystemPrompt) { mutableStateOf(settings.chatSystemPrompt) }
  var providerExpanded by remember { mutableStateOf(false) }
  var modelExpanded by remember { mutableStateOf(false) }
  var baseUrlText by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }

  val providers = listOf("Gemini", "Custom", "Free Public (Pollinations.ai)")
  val models = listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash", "gemini-2.0-pro", "llama-3-70b", "mixtral-8x7b", "openai")

  Column(
    modifier = Modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // ── Info card (when API key is blank) ──
    if (settings.apiKey.isBlank() && settings.apiProvider == "Gemini") {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .size(20.dp)
              .padding(top = 2.dp)
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              "How to get a free API key",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              "Visit aistudio.google.com → Get API key → Copy and paste it below.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    // ── Warning card (when Free Public is selected) ──
    AnimatedVisibility(visible = settings.apiProvider == "Free Public (Pollinations.ai)") {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
              .size(20.dp)
              .padding(top = 2.dp)
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              "Privacy Warning",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
              "This uses a public, unauthenticated API. Do not send sensitive or personal data, as it may be logged by the API provider.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }
      }
    }

    // ── API Key field ──
    OutlinedTextField(
      value = apiKeyText,
      onValueChange = { apiKeyText = it },
      label = { Text("API Key") },
      placeholder = { Text("AIzaSy…") },
      singleLine = true,
      visualTransformation = if (apiKeyVisible) VisualTransformation.None
      else PasswordVisualTransformation(),
      trailingIcon = {
        Row {
          IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
            Icon(
              if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = "Toggle visibility"
            )
          }
          if (apiKeyText != settings.apiKey) {
            IconButton(onClick = { onSettingsChange(settings.copy(apiKey = apiKeyText.trim())) }) {
              Icon(
                Icons.Default.Check,
                contentDescription = "Save API key",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp)
    )

    // ── API Provider selector ──
    ExposedDropdownMenuBox(
      expanded = providerExpanded,
      onExpandedChange = { providerExpanded = it }
    ) {
      OutlinedTextField(
        value = settings.apiProvider,
        onValueChange = {},
        readOnly = true,
        label = { Text("API Provider") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
        modifier = Modifier
          .fillMaxWidth()
          .menuAnchor(),
        shape = RoundedCornerShape(12.dp)
      )
      ExposedDropdownMenu(
        expanded = providerExpanded,
        onDismissRequest = { providerExpanded = false }
      ) {
        providers.forEach { provider ->
          DropdownMenuItem(
            text = { Text(provider) },
            onClick = {
              onSettingsChange(settings.copy(apiProvider = provider))
              providerExpanded = false
            },
            trailingIcon = {
              if (provider == settings.apiProvider) {
                Icon(
                  Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          )
        }
      }
    }

    // ── API Base URL (Custom Provider Only) ──
    AnimatedVisibility(visible = settings.apiProvider == "Custom") {
      OutlinedTextField(
        value = baseUrlText,
        onValueChange = { baseUrlText = it },
        label = { Text("API Base URL") },
        placeholder = { Text("http://localhost:1234/v1") },
        singleLine = true,
        trailingIcon = {
          if (baseUrlText != settings.apiBaseUrl) {
            IconButton(onClick = { onSettingsChange(settings.copy(apiBaseUrl = baseUrlText.trim())) }) {
              Icon(
                Icons.Default.Check,
                contentDescription = "Save Base URL",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp)
      )
    }

    // ── Model selector (ExposedDropdownMenuBox) ──
    ExposedDropdownMenuBox(
      expanded = modelExpanded,
      onExpandedChange = { modelExpanded = it }
    ) {
      OutlinedTextField(
        value = settings.selectedModel,
        onValueChange = {},
        label = { Text(if (settings.apiProvider == "Custom") "Model ID" else "Gemini Model") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
        modifier = Modifier
          .fillMaxWidth()
          .menuAnchor(),
        shape = RoundedCornerShape(12.dp)
      )
      ExposedDropdownMenu(
        expanded = modelExpanded,
        onDismissRequest = { modelExpanded = false }
      ) {
        models.forEach { model ->
          DropdownMenuItem(
            text = { Text(model) },
            onClick = {
              onSettingsChange(settings.copy(selectedModel = model))
              modelExpanded = false
            },
            leadingIcon = {
              Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
              if (model == settings.selectedModel) {
                Icon(
                  Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          )
        }
      }
    }
    
    // Custom Model Input if Custom Provider
    AnimatedVisibility(visible = settings.apiProvider == "Custom") {
      OutlinedTextField(
        value = settings.selectedModel,
        onValueChange = { onSettingsChange(settings.copy(selectedModel = it)) },
        label = { Text("Custom Model ID (or select above)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp)
      )
    }

    // ── System Prompt field ──
    OutlinedTextField(
      value = promptText,
      onValueChange = { promptText = it },
      label = { Text("System Prompt") },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 120.dp),
      shape = RoundedCornerShape(12.dp),
      maxLines = 8,
      supportingText = {
        Text(
          "Instructions sent to Gemini for categorization",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    )

    // ── Save prompt button (shows only when prompt has changed) ──
    AnimatedVisibility(
      visible = promptText != settings.systemPrompt,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically()
    ) {
      FilledTonalButton(
        onClick = { onSettingsChange(settings.copy(systemPrompt = promptText)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
      ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save Categorization Prompt")
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ── Chat System Prompt field ──
    OutlinedTextField(
      value = chatPromptText,
      onValueChange = { chatPromptText = it },
      label = { Text("Chat System Prompt") },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 120.dp),
      shape = RoundedCornerShape(12.dp),
      maxLines = 8,
      supportingText = {
        Text(
          "Instructions sent to Gemini for the chat assistant",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    )

    AnimatedVisibility(
      visible = chatPromptText != settings.chatSystemPrompt,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically()
    ) {
      FilledTonalButton(
        onClick = { onSettingsChange(settings.copy(chatSystemPrompt = chatPromptText)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
      ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save Chat Prompt")
      }
    }

    Spacer(modifier = Modifier.height(4.dp))
  }
}

// ─────────────────────────────────────────────────────────────
// Reusable Components
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
  )
}

@Composable
private fun SettingsToggleRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 0.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange
      )
    }
  }
}

// ─────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────

@Composable
private fun ClearDataDialog(
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        Icons.Default.DeleteForever,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error
      )
    },
    title = { Text("Clear all data?") },
    text = { Text("This permanently deletes all saved items. This action cannot be undone.") },
    confirmButton = {
      Button(
        onClick = onConfirm,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError
        )
      ) { Text("Clear") }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(28.dp)
      ) { Text("Cancel") }
    },
    shape = RoundedCornerShape(28.dp)
  )
}

@Composable
private fun ImportBackupDialog(
  onDismiss: () -> Unit,
  onImport: (String) -> Unit
) {
  var json by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(Icons.Default.Download, contentDescription = null)
    },
    title = { Text("Import Backup") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "Paste your exported JSON backup below.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
          value = json,
          onValueChange = { json = it },
          placeholder = { Text("[{\"id\":\"…\", …}]") },
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
          shape = RoundedCornerShape(12.dp),
          maxLines = 8
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { if (json.isNotBlank()) onImport(json.trim()) },
        enabled = json.isNotBlank(),
        shape = RoundedCornerShape(28.dp)
      ) { Text("Restore") }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(28.dp)
      ) { Text("Cancel") }
    },
    shape = RoundedCornerShape(28.dp)
  )
}
