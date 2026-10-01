package com.ritikagarwal.koshvista

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ritikagarwal.koshvista.data.VaultDatabase
import com.ritikagarwal.koshvista.data.VaultFactory
import com.ritikagarwal.koshvista.ui.FinanceApp
import com.ritikagarwal.koshvista.ui.KoshVistaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KoshVistaTheme { AppEntry() } }
    }
}

@Composable
private fun AppEntry() {
    val context = LocalContext.current
    val database = remember { mutableStateOf<VaultDatabase?>(null) }
    DisposableEffect(database.value) { onDispose { database.value?.close() } }
    val active = database.value
    if (active != null) {
        FinanceApp("debug-local", active)
    } else Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("KoshVista", style = MaterialTheme.typography.headlineLarge)
            Text("A clearer view of your money", style = MaterialTheme.typography.bodyLarge)
            Text("Financial records stay on this phone. Google sign-in and encrypted Drive recovery require the app's OAuth configuration.",
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
            if (BuildConfig.DEBUG) Button(onClick = { database.value = VaultFactory(context).open("debug-local") }) {
                Text("Open local development vault")
            } else Text("Sign-in setup is required before this build can hold financial records.")
        }
    }
}
