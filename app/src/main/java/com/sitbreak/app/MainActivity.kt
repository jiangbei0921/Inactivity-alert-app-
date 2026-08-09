package com.sitbreak.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sitbreak.app.navigation.MainNavHost
import com.sitbreak.app.ui.theme.SitBreakTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge 在个别国产 ROM 上可能抛异常，包一层避免整个 Activity 启动即崩。
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.e("MainActivity", "enableEdgeToEdge failed", e)
        }
        setContent {
            SitBreakTheme {
                // 读取上次崩溃日志；若有过崩溃则在启动时弹出，方便用户一键复制反馈。
                var crashText by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        crashText = CrashReporter.read(this@MainActivity)
                    }
                }
                crashText?.let { text ->
                    CrashReportDialog(
                        text = text,
                        onDismiss = {
                            crashText = null
                            CrashReporter.clear(this@MainActivity)
                        },
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionState = rememberPermissionState(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                    LaunchedEffect(Unit) {
                        if (!permissionState.status.isGranted) {
                            permissionState.launchPermissionRequest()
                        }
                    }
                }
                MainNavHost()
            }
        }
    }
}

@Composable
private fun CrashReportDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                onDismiss()
            }) { Text("复制并关闭") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("上次运行已崩溃，已记录") },
        text = {
            Text(
                text = text,
                fontSize = 11.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}
