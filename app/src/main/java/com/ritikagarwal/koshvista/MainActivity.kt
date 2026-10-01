package com.ritikagarwal.koshvista

import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ritikagarwal.koshvista.data.VaultDatabase
import com.ritikagarwal.koshvista.data.VaultFactory
import com.ritikagarwal.koshvista.identity.GoogleAuthGateway
import com.ritikagarwal.koshvista.ui.FinanceApp
import com.ritikagarwal.koshvista.ui.KoshVistaTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KoshVistaTheme { AppEntry() } }
    }
}

@Composable
private fun AppEntry() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var database by remember { mutableStateOf<VaultDatabase?>(null) }
    var ownerId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val active = database
    DisposableEffect(active) { onDispose { active?.close() } }
    if (active != null) {
        FinanceApp(checkNotNull(ownerId), active, onSignOut = {
            database = null
            ownerId = null
        }, onDeleteLocal = {
            val toDelete = checkNotNull(ownerId)
            active.close()
            deleting = true
            database = null
            ownerId = null
            scope.launch {
                try { withContext(Dispatchers.IO) { VaultFactory(context).delete(toDelete) }; error = null }
                catch (failure: Exception) { error = failure.message ?: "Could not delete local vault" }
                finally { deleting = false }
            }
        })
    } else Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("KoshVista", style = MaterialTheme.typography.headlineLarge)
            Text("A clearer view of your money", style = MaterialTheme.typography.bodyLarge)
            Text("Financial records stay on this phone. Google sign-in and encrypted Drive recovery require the app's OAuth configuration.",
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
            if (deleting) Text("Deleting this device's vault…")
            if (!deleting && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) Button(onClick = {
                scope.launch {
                    try {
                        val signedIn = GoogleAuthGateway().signIn(context as Activity, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        ownerId = signedIn.subjectId
                        database = VaultFactory(context).open(signedIn.subjectId)
                        error = null
                    } catch (failure: Exception) { error = failure.message ?: "Google sign-in failed" }
                }
            }) { Text("Continue with Google") }
            if (!deleting && BuildConfig.DEBUG) Button(onClick = {
                ownerId = "debug-local"
                database = VaultFactory(context).open("debug-local")
            }) {
                Text("Open local development vault")
            } else if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) Text("Sign-in setup is required before this build can hold financial records.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
