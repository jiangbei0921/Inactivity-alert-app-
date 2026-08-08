package com.sitbreak.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 组件级 Compose UI 测试：验证统一的 [AppCard] 容器能正常渲染并承载内容。
 *
 * 这类测试在 androidTest（instrumentation）下运行，需要连接设备/模拟器
 * （`connectedAndroidTest`）。CI 的 `assembleDebug` 会编译本测试以验证 API 用法正确。
 */
@RunWith(AndroidJUnit4::class)
class AppCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appCard_rendersProvidedContent() {
        composeTestRule.setContent {
            MaterialTheme {
                AppCard {
                    Text("今日站立 12 次")
                }
            }
        }
        composeTestRule.onNodeWithText("今日站立 12 次").assertExists()
    }
}
