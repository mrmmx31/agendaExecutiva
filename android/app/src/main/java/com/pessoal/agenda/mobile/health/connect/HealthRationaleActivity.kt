package com.pessoal.agenda.mobile.health.connect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pessoal.agenda.mobile.ui.theme.AgendaMobileTheme

class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AgendaMobileTheme { HealthRationaleScreen() } }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HealthRationaleScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Privacidade de saúde") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Finalidade", style = MaterialTheme.typography.titleMedium)
            Text("A Agenda lê somente as categorias escolhidas para montar um relatório que você revisa antes de exportar.")
            Text("Armazenamento", style = MaterialTheme.typography.titleMedium)
            Text("Amostras brutas não são copiadas. Resumos, cobertura e origem ficam cifrados neste aparelho com chave do Android Keystore.")
            Text("Controle", style = MaterialTheme.typography.titleMedium)
            Text("Cada categoria pode ser desligada separadamente. Negar ou revogar não afeta tarefas, protocolos, capturas ou alertas.")
            Text("Compartilhamento", style = MaterialTheme.typography.titleMedium)
            Text("Nada é enviado automaticamente. Dados de saúde não entram em recomendações, diagnóstico ou ajuste de medicação.")
            Text("Limites atuais", style = MaterialTheme.typography.titleMedium)
            Text("A leitura ocorre somente enquanto a tela está aberta e cobre no máximo os sete dias solicitados. A Agenda não pede acesso histórico ampliado nem leitura em segundo plano.")
        }
    }
}
