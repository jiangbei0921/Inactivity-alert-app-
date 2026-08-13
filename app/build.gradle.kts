import java.util.Base64

/**
 * 版本号必须单调递增，否则增量更新无从判断新旧、系统也会拒绝降级安装。
 *
 * 取值优先级：
 * 1. 环境变量 VERSION_CODE —— 应急覆盖（手工出包、补发某个版本）
 * 2. git 提交数 —— main 线性历史下天然单调递增，且本地与 CI 结果一致
 *    （CI 必须用 fetch-depth: 0，浅克隆只能数到 1）
 * 3. 兜底 1 —— 没有 git 的纯源码包场景，至少保证能编译
 */
fun resolveVersionCode(): Int {
    System.getenv("VERSION_CODE")?.trim()?.toIntOrNull()?.let { if (it > 0) return it }
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        output.toIntOrNull()?.takeIf { it > 0 } ?: 1
    } catch (e: Exception) {
        logger.warn("Cannot resolve versionCode from git (${e.message}), falling back to 1")
        1
    }
}

val appVersionCode = resolveVersionCode()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.sitbreak.app"
    compileSdk = 34

    defaultConfig {
        // 注意：namespace 保持不变（源码/R/BuildConfig 仍用 com.sitbreak.app），
        // 仅改 applicationId 以全新包名安装，绕开华为手机上残留的旧 com.sitbreak.app
        // （普通卸载清不掉的应用分身/隐私空间/多用户副本），彻底消除「签名不一致」。
        applicationId = "com.standbreak.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = "1.1.$appVersionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 增量更新的清单地址。托管在 CloudStudio 静态站（腾讯系，国内直连，
        // 不需要科学上网），与下载页、二维码指向同一站点，发布时一起更新。
        // 可用环境变量覆盖，方便自建源或本地联调。
        val updateManifestUrl = System.getenv("UPDATE_MANIFEST_URL")
            ?: "https://ff39a623aaed4aecbd47ab262b992462.bj9.agentos-app.net/update.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }

    buildTypes {
        release {
            // 关闭混淆/压缩，确保 Release 包与 Debug 行为一致、稳定可运行
            // （本项目曾因机型兼容问题，保守起见 Release 也关闭混淆）
            isMinifyEnabled = false
            isShrinkResources = false
            // 支持两种签名密钥注入方式：
            // 1) SIGNING_KEYSTORE：指向本地 keystore 文件路径（本地开发/某些 CI 场景）
            // 2) SIGNING_KEYSTORE_BASE64：将 keystore 文件 base64 编码后通过环境变量注入（GitHub Secrets 推荐）
            // 四组信息（storeFile/storePassword/keyAlias/keyPassword）全部齐全时才启用签名。
            val keystorePath = System.getenv("SIGNING_KEYSTORE")
            val keystoreBase64 = System.getenv("SIGNING_KEYSTORE_BASE64")
            val storePasswordEnv = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("SIGNING_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("SIGNING_KEY_PASSWORD")

            val effectiveKeystoreFile = when {
                keystorePath != null && file(keystorePath).exists() -> file(keystorePath)
                keystoreBase64 != null -> {
                    val tempFile = rootProject.layout.buildDirectory.file("signing/release.keystore").get().asFile
                    tempFile.parentFile.mkdirs()
                    tempFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
                    tempFile
                }
                else -> null
            }

            if (effectiveKeystoreFile != null
                && storePasswordEnv != null
                && keyAliasEnv != null
                && keyPasswordEnv != null
            ) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = effectiveKeystoreFile
                    storePassword = storePasswordEnv
                    keyAlias = keyAliasEnv
                    keyPassword = keyPasswordEnv
                    // 本仓库的签名密钥由 Python 生成，格式为 PKCS12
                    storeType = "PKCS12"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // AGP 8 默认关闭 BuildConfig 生成；增量更新需要读取 UPDATE_MANIFEST_URL
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Hilt 2.50 的 hiltAggregateDeps 聚合任务会与 Room(经 ksp 引入) 争夺 javapoet 版本，
// 导致 NoSuchMethodError: ClassName.canonicalName()（冲突发生在插件自身类路径，resolutionStrategy.force 无法触及）。
// 关闭聚合任务让 Hilt 按模块独立处理，避开该冲突（官方推荐方案）。
hilt {
    enableAggregatingTask = false
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.core.ktx)

    implementation(libs.accompanist.permissions)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}