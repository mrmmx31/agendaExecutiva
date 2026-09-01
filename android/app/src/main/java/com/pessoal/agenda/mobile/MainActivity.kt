package com.pessoal.agenda.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.pessoal.agenda.mobile.ui.AgendaMobileApp
import android.content.Intent

class MainActivity : ComponentActivity() {
    private val pairingInvitation = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingInvitation.value = intent.pairingInvitation()
        enableEdgeToEdge()
        setContent { AgendaMobileApp(pairingInvitation.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pairingInvitation.value = intent.pairingInvitation()
    }

    private fun Intent.pairingInvitation(): String? = dataString
        ?.takeIf { data?.scheme == "agenda" && data?.host == "pair" }
}
