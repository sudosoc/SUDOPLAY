package com.sudosoc.sudoplay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.sudosoc.sudoplay.ui.SudoApp

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    private val audioPerm: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudio() = ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: android.content.Intent?) {
        when (i?.action) {
            ACTION_OPEN_PLAYER -> vm.requestPlayer()
            "com.sudosoc.sudoplay.SHUFFLE_ALL" -> { vm.requestPlayer(); vm.whenReady { vm.shuffleAll() } }
            "com.sudosoc.sudoplay.RESUME" -> { vm.requestPlayer(); vm.whenReady { vm.resumeNow() } }
            "com.sudosoc.sudoplay.CAR" -> vm.carMode = true
        }
    }

    companion object { const val ACTION_OPEN_PLAYER = "com.sudosoc.sudoplay.OPEN_PLAYER" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            var granted by remember { mutableStateOf(hasAudio()) }
            val perms = remember {
                buildList {
                    add(audioPerm)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                granted = hasAudio()
                if (granted) vm.refreshLibrary()
            }
            LaunchedEffect(Unit) {
                if (granted) vm.refreshLibrary() else launcher.launch(perms)
            }
            SudoApp(vm, granted) { launcher.launch(perms) }
        }
    }
}
