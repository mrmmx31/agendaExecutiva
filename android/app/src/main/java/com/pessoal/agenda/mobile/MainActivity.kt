package com.pessoal.agenda.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.pessoal.agenda.mobile.ui.AgendaMobileApp
import com.pessoal.agenda.mobile.wear.PhoneWearActionReconciler
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pairingInvitation = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pairingInvitation.value = intent.pairingInvitation()
        }
        enableEdgeToEdge()
        setContent { AgendaMobileApp(pairingInvitation.value) }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { PhoneWearActionReconciler(applicationContext).reconcile() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pairingInvitation.value = intent.pairingInvitation()
    }

    private fun Intent.pairingInvitation(): String? = dataString
        ?.takeIf { data?.scheme == "agenda" && data?.host == "pair" }
}
