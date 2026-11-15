package br.com.remediocerto.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.remediocerto.core.seguranca.Biometria

/**
 * Abertura do app com a sessão salva e a biometria ligada (RF02 / CT03).
 * Pede a biometria logo ao abrir; quem preferir entra com a senha.
 */
@Composable
fun DesbloqueioScreen(
    nome: String,
    onDesbloqueado: () -> Unit,
    onUsarSenha: () -> Unit,
) {
    val context = LocalContext.current
    var erro by remember { mutableStateOf<String?>(null) }

    fun pedirBiometria() {
        erro = null
        Biometria.autenticar(
            context = context,
            titulo = "Entrar no Remédio Certo",
            onSucesso = onDesbloqueado,
            onUsarSenha = onUsarSenha,
            onErro = { erro = it },
        )
    }

    LaunchedEffect(Unit) { pedirBiometria() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Remédio Certo",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text("Olá, ${nome.substringBefore(" ")}!", style = MaterialTheme.typography.titleMedium)
        Text(
            "Confirme sua identidade para continuar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        erro?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { pedirBiometria() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Entrar com biometria")
        }
        TextButton(onClick = onUsarSenha, modifier = Modifier.fillMaxWidth()) {
            Text("Entrar com senha")
        }
    }
}
