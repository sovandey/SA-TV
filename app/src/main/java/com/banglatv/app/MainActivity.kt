package com.banglatv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BanglaTheme { App(vm) }
        }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val gridState = rememberLazyGridState()
    BackHandler(enabled = vm.nowPlaying != null) { vm.nowPlaying = null }
    Box(Modifier.fillMaxSize().background(Bg)) {
        val playing = vm.nowPlaying
        if (playing != null) {
            PlayerScreen(vm, playing)
        } else {
            HomeScreen(vm, gridState)
        }
    }
}
