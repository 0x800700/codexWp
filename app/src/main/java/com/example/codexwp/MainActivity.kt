package com.example.codexwp

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.codexwp.ui.SettingsActivity
import com.example.codexwp.ui.theme.CodexWpTheme
import com.example.codexwp.wallpaper.AmoledWaveWallpaperService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexWpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LauncherScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSetWallpaper = { launchWallpaperPicker() },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
                    )
                }
            }
        }
    }

    private fun launchWallpaperPicker() {
        val component = ComponentName(this, AmoledWaveWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}

@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    onSetWallpaper: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "AMOLED Wave")
        Button(onClick = onSetWallpaper, modifier = Modifier.padding(top = 16.dp)) {
            Text("Set Live Wallpaper")
        }
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
            Text("Open Settings")
        }
    }
}
