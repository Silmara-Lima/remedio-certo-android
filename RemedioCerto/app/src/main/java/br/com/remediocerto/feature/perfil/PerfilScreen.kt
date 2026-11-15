package br.com.remediocerto.feature.perfil

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.core.seguranca.Biometria
import br.com.remediocerto.core.seguranca.Disponibilidade
import br.com.remediocerto.core.seguranca.textoDisponibilidade

/** T18 – Perfil e preferências: dados do usuário, biometria, notificações, trocar senha e sair. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    usuario: UsuarioDto,
    onVoltar: () -> Unit,
    onTrocarSenha: () -> Unit,
    onSair: () -> Unit,
) {
    val context = LocalContext.current
    val disponibilidade = remember { Biometria.disponibilidade(context) }
    var biometriaAtiva by remember { mutableStateOf(Biometria.ativada(context)) }
    var aviso by remember { mutableStateOf<String?>(null) }

    fun alternarBiometria(ligar: Boolean) {
        aviso = null
        if (!ligar) {
            Biometria.definirAtivada(context, false)
            biometriaAtiva = false
            return
        }
        // Só liga depois de uma leitura bem-sucedida: confirma que o sensor funciona para esta pessoa.
        Biometria.autenticar(
            context = context,
            titulo = "Ativar login por biometria",
            onSucesso = {
                Biometria.definirAtivada(context, true)
                biometriaAtiva = true
                aviso = "Pronto! Da próxima vez, entre com a biometria."
            },
            onUsarSenha = {},
            onErro = { aviso = it },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil e preferências") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Dados ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(usuario.nome, style = MaterialTheme.typography.titleLarge)
                    Text(usuario.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Perfil: " + if (usuario.tipo == "ADMIN") "Administrador" else "Funcionário",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ---------- Segurança ----------
            Text("Segurança", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Entrar com biometria", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Digital ou rosto, no lugar de digitar a senha ao abrir o app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = biometriaAtiva,
                            onCheckedChange = ::alternarBiometria,
                            enabled = disponibilidade == Disponibilidade.DISPONIVEL,
                        )
                    }
                    textoDisponibilidade(disponibilidade)?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    aviso?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = onTrocarSenha, modifier = Modifier.fillMaxWidth()) { Text("Trocar senha") }

            // ---------- Notificações ----------
            Text("Notificações", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = {
                    // Abre a tela do Android onde a pessoa liga ou desliga as notificações do app.
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Configurar notificações de alertas") }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSair,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sair da conta") }
        }
    }
}
