package com.leslie.teachtrack

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leslie.teachtrack.data.TeachTrackRepository

@Composable
fun TeachTrackShell(repo: TeachTrackRepository) {
    val context = LocalContext.current.applicationContext
    val vm: TeachTrackViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TeachTrackViewModel(repo, context) as T
    })
    var composerVisible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        TeachTrackApp(repo)
        if (composerVisible) {
            CopilotComposer(vm.copilot)
        }
        FloatingActionButton(
            onClick = { composerVisible = !composerVisible },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 92.dp),
            containerColor = DeepGreen,
            contentColor = androidx.compose.ui.graphics.Color.White
        ) { Icon(Icons.Rounded.AutoAwesome, if (composerVisible) "关闭 Copilot" else "打开 Copilot") }
    }
}
