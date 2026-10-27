package com.ningguang.skip

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.ui.glow.FloatingIslandNavBar
import com.ningguang.skip.ui.glow.IslandNavItem
import com.ningguang.skip.ui.glow.NingguangBackground
import com.ningguang.skip.ui.glow.NingguangConfig
import com.ningguang.skip.ui.screens.HomeScreen
import com.ningguang.skip.ui.screens.LogScreen
import com.ningguang.skip.ui.screens.RulesScreen
import com.ningguang.skip.ui.screens.SettingsScreen
import com.ningguang.skip.ui.theme.NingguangTheme

class MainActivity : ComponentActivity() {

    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响跳过能力，只是没有提示 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { NingguangSkipApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 从无障碍设置页返回时刷新状态（onResume 里也会刷新）
    }
}

private sealed class Tab(val route: String, val label: String) {
    data object Home : Tab("home", "首页")
    data object Rules : Tab("rules", "规则")
    data object Log : Tab("log", "日志")
    data object Settings : Tab("settings", "设置")
}

@Composable
private fun NingguangSkipApp() {
    val settings by Prefs.settingsFlow.collectAsStateWithLifecycle()
    val dark = if (settings.followSystemTheme) {
        androidx.compose.foundation.isSystemInDarkTheme()
    } else settings.darkMode

    LaunchedEffect(settings.glowEnabled, settings.glowIntensity) {
        NingguangConfig.enabled = settings.glowEnabled
        NingguangConfig.intensity = settings.glowIntensity
    }

    NingguangTheme(darkTheme = dark, followSystem = settings.followSystemTheme) {
        NingguangBackground(dark = dark) {
            val navController = rememberNavController()
            var selected by remember { mutableIntStateOf(0) }
            val tabs = remember {
                listOf(
                    IslandNavItem(Tab.Home.label, Icons.Rounded.Home),
                    IslandNavItem(Tab.Rules.label, Icons.Rounded.Rule),
                    IslandNavItem(Tab.Log.label, Icons.Rounded.History),
                    IslandNavItem(Tab.Settings.label, Icons.Rounded.Settings),
                )
            }

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    FloatingIslandNavBar(
                        items = tabs,
                        selectedIndex = selected,
                        onSelect = { index ->
                            selected = index
                            navController.navigate(
                                when (index) {
                                    1 -> Tab.Rules.route
                                    2 -> Tab.Log.route
                                    3 -> Tab.Settings.route
                                    else -> Tab.Home.route
                                }
                            ) {
                                popUpTo(Tab.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Tab.Home.route,
                        // 流体转场：淡入 + 轻微横向位移 + 缩放，模拟「元素自然衔接」
                        enterTransition = {
                            (fadeIn(androidx.compose.animation.core.tween(220)) +
                                slideInHorizontally(
                                    animationSpec = spring(),
                                    initialOffsetX = { it / 6 }
                                ) + scaleIn(initialScale = 0.97f))
                        },
                        exitTransition = { fadeOut(androidx.compose.animation.core.tween(150)) },
                        popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(200)) },
                        popExitTransition = {
                            fadeOut(androidx.compose.animation.core.tween(150)) +
                                slideOutHorizontally(targetOffsetX = { it / 6 })
                        }
                    ) {
                        composable(Tab.Home.route) { HomeScreen() }
                        composable(Tab.Rules.route) { RulesScreen() }
                        composable(Tab.Log.route) { LogScreen() }
                        composable(Tab.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }
    }
}
