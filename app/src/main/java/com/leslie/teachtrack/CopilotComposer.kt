package com.leslie.teachtrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leslie.teachtrack.copilot.CopilotCandidate
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

@Composable
fun CopilotComposer(service: com.leslie.teachtrack.copilot.CopilotService) {
    var input by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<CopilotCandidate?>(null) }
    var voiceStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) { input = text; voiceStatus = "语音已转为可编辑文本" }
        else voiceStatus = "未获得语音结果，请检查系统语音服务或直接输入文字"
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要记录的内容")
        })
    }
    SectionCard("TeachTrack Copilot", Icons.Rounded.AutoAwesome) {
        Text("用一句话记录学生、课程、成绩或待办；发送前会先预览，不会静默写入。", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("例如：给王晨然添加一个周五检查作业的待办") })
            IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要记录的内容")
                    })
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Rounded.Mic, "语音输入") }
        }
        if (voiceStatus.isNotBlank()) Text(voiceStatus, color = DeepGreen, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { scope.launch { candidate = service.interpret(input) } }, enabled = input.isNotBlank()) {
            Text("识别并预览")
        }
        candidate?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(result.error ?: result.explanation, color = DeepGreen)
            result.command?.let { command ->
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { candidate = result.copy(explanation = service.execute(command, true).message) } }) { Text("确认执行") }
                    Text("动作：${command.action}")
                }
            }
        }
    }
}
