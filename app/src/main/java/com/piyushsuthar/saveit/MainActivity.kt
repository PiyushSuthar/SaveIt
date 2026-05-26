package com.piyushsuthar.saveit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.piyushsuthar.saveit.data.DefaultDataRepository
import com.piyushsuthar.saveit.theme.TestAppTheme
import com.piyushsuthar.saveit.ui.share.ShareReceiveSheet

class MainActivity : ComponentActivity() {

  // Holds the text/URL that was shared into the app
  private val sharedText = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize data repository singleton
    DefaultDataRepository.initialize(applicationContext)

    // Extract share intent text on first launch
    extractSharedText(intent)

    enableEdgeToEdge()
    setContent {
      TestAppTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainNavigation()

          // Overlay share sheet when a share intent is received
          val shared = sharedText.value
          if (shared != null) {
            ShareReceiveSheet(
              sharedText = shared,
              onDismiss = { sharedText.value = null }
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractSharedText(intent)
  }

  /**
   * Extracts text/URL from a share or view intent and stores it in [sharedText].
   * Handles:
   *  - ACTION_SEND  text/plain   (most share sheets)
   *  - ACTION_SEND  text/html    (some browsers / email apps)
   *  - ACTION_VIEW  (direct URL view intent from browser address bar)
   */
  private fun extractSharedText(intent: Intent?) {
    if (intent == null) return

    val text: String? = when {
      intent.action == Intent.ACTION_SEND -> {
        // text/plain and text/html both carry EXTRA_TEXT
        intent.getStringExtra(Intent.EXTRA_TEXT)
          ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
      }
      intent.action == Intent.ACTION_VIEW -> {
        // Browser "share URL" or direct deep link
        intent.dataString
      }
      else -> null
    }

    if (!text.isNullOrBlank()) {
      sharedText.value = text.trim()
    }
  }
}
