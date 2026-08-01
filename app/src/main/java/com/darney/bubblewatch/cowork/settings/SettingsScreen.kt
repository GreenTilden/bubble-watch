package com.darney.bubblewatch.cowork.settings

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.darney.bubblewatch.cowork.input.rememberVoiceInput
import com.darney.bubblewatch.data.BridgeConfig
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.SettingsStore
import com.darney.bubblewatch.ui.rotaryScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BridgeRepository.get(app)
    private val _config = MutableStateFlow(BridgeConfig(SettingsStore.DEFAULT_BASE_URL, ""))
    val config: StateFlow<BridgeConfig> = _config.asStateFlow()

    init {
        viewModelScope.launch { repo.configFlow.collect { _config.value = it } }
    }

    fun setBaseUrl(url: String) = save(url, _config.value.token)
    fun setToken(token: String) = save(_config.value.baseUrl, token)

    private fun save(url: String, token: String) {
        viewModelScope.launch { repo.settings.setConfig(url, token) }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }

    val editUrl = rememberVoiceInput(label = "Bridge URL") { vm.setBaseUrl(it) }
    val editToken = rememberVoiceInput(label = "Token") { vm.setToken(it) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
            modifier = Modifier.rotaryScroll(listState, focusRequester),
        ) {
            item { ListHeader { Text("Settings") } }
            item {
                Chip(
                    label = { Text("Bridge URL") },
                    secondaryLabel = { Text(config.baseUrl.ifBlank { "(unset)" }, maxLines = 2) },
                    onClick = editUrl,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    label = { Text("Token") },
                    secondaryLabel = { Text(if (config.token.isBlank()) "(unset)" else "•••• set") },
                    onClick = editToken,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = if (config.isConfigured) "✓ configured" else "Enter URL + token",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
