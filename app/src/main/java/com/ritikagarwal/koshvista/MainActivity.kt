package com.ritikagarwal.koshvista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ritikagarwal.koshvista.ui.KoshVistaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KoshVistaTheme { LaunchScreen() } }
    }
}

@Composable
private fun LaunchScreen() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("KoshVista", style = MaterialTheme.typography.headlineLarge)
            Text("A clearer view of your money", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
