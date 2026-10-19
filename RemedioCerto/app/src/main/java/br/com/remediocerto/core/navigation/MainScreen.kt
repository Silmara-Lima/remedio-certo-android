package br.com.remediocerto.core.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.feature.estoque.EstoqueScreen
import br.com.remediocerto.feature.movimentacao.NovaMovimentacaoScreen

enum class Aba(val rota: String, val titulo: String, val icone: ImageVector) {
    INICIO("inicio", "Início", Icons.Filled.Home),
    ESTOQUE("estoque", "Estoque", Icons.AutoMirrored.Filled.List),
    ESCANEAR("escanear", "Escanear", Icons.Filled.Search),
    ALERTAS("alertas", "Alertas", Icons.Filled.Notifications),
    MAIS("mais", "Mais", Icons.Filled.Menu),
}

@Composable
fun MainScreen(usuario: UsuarioDto, onSair: () -> Unit) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach { aba ->
                    NavigationBarItem(
                        selected = rotaAtual == aba.rota,
                        onClick = {
                            nav.navigate(aba.rota) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(aba.icone, contentDescription = aba.titulo) },
                        label = { Text(aba.titulo) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Aba.INICIO.rota, modifier = Modifier.padding(padding)) {
            composable(Aba.INICIO.rota) { TelaProvisoria("Início", "Olá, ${usuario.nome}!") }
            composable(Aba.ESTOQUE.rota) { EstoqueScreen() }
            composable(Aba.ESCANEAR.rota) { NovaMovimentacaoScreen(usuario, abrirScanner = true) }
            composable(Aba.ALERTAS.rota) { TelaProvisoria("Alertas") }
            composable(Aba.MAIS.rota) { MaisScreen(usuario, onSair) }
        }
    }
}

@Composable
fun TelaProvisoria(titulo: String, subtitulo: String = "Em construção") {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(subtitulo)
    }
}

@Composable
fun MaisScreen(usuario: UsuarioDto, onSair: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(usuario.nome, style = MaterialTheme.typography.headlineSmall)
        Text(usuario.email)
        Text("Perfil: ${usuario.tipo}")
        Spacer(Modifier.height(24.dp))
        if (usuario.tipo == "ADMIN") {
            Text("Medicamentos · Unidades · Usuários · Relatórios (em breve)")
            Spacer(Modifier.height(24.dp))
        }
        OutlinedButton(onClick = onSair) { Text("Sair") }
    }
}