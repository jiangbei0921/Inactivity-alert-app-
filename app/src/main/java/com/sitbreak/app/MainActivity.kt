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
                // 同步读取上次崩溃日志（文件很小，主线程读取无碍），确保对话框在第一帧就出现，
                // 避免崩溃死循环时主界面先渲染又崩、用户永远看不到日志。
                var crashText by remember { mutableStateOf(CrashReporter.read(this@MainActivity)) }
                crashText?.let { text ->
                    CrashReportDialog(
                        text = text,
                        onDismiss = {
                            crashText = null
                            CrashReporter.clear(this@MainActivity)
                        },
                    )
                }

                // 若已有上次崩溃记录，先只弹对话框、暂不渲染主界面，避免「崩溃-重启-再崩溃」死循环
                // 导致用户永远看不到崩溃日志。关闭对话框（crashText 置空）后才会渲染主界面。
                if (crashText != null) return@SitBreakTheme

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
