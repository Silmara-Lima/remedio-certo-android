package br.com.remediocerto.core.navigation

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.core.notificacao.Notificacoes
import br.com.remediocerto.core.seguranca.Biometria

import br.com.remediocerto.feature.RelatoriosScreen
import br.com.remediocerto.feature.cadastros.medicamentos.MedicamentoFormScreen
import br.com.remediocerto.feature.cadastros.medicamentos.MedicamentosScreen
import br.com.remediocerto.feature.cadastros.unidades.UnidadeFormScreen
import br.com.remediocerto.feature.cadastros.unidades.UnidadesScreen
import br.com.remediocerto.feature.alertas.AlertasScreen
import br.com.remediocerto.feature.alertas.configuracao.ConfiguracaoAlertasScreen
import br.com.remediocerto.feature.dashboard.DashboardScreen
import br.com.remediocerto.feature.estoque.EstoqueScreen
import br.com.remediocerto.feature.estoque.MedicamentoDetalheScreen
import br.com.remediocerto.feature.historico.HistoricoScreen
import br.com.remediocerto.feature.movimentacao.NovaMovimentacaoScreen
import br.com.remediocerto.feature.cadastros.usuarios.UsuarioFormScreen
import br.com.remediocerto.feature.cadastros.usuarios.UsuariosScreen
import br.com.remediocerto.feature.perfil.PerfilScreen
import br.com.remediocerto.feature.perfil.TrocarSenhaScreen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// ---------- Abas principais ----------

enum class Aba(
    val rota: String,
    val titulo: String,
    val icone: ImageVector
) {
    INICIO("inicio", "Início", Icons.Filled.Home),
    ESTOQUE("estoque", "Estoque", Icons.AutoMirrored.Filled.List),
    ESCANEAR("escanear", "Escanear", Icons.Filled.Search),
    ALERTAS("alertas", "Alertas", Icons.Filled.Notifications),
    MAIS("mais", "Mais", Icons.Filled.Menu),
}

// ---------- Tela principal ----------

@Composable
fun MainScreen(
    usuario: UsuarioDto,
    onSair: () -> Unit,
    abrirAlertas: Boolean = false,          // true quando o app abriu pelo toque na notificação
    onAlertasAbertos: () -> Unit = {},
    oferecerBiometria: Boolean = false,     // logo depois do login com senha (Fluxo C)
    onOfertaBiometriaRespondida: () -> Unit = {},
) {

    val nav = rememberNavController()

    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    fun irParaAba(rota: String) {
        nav.navigate(rota) {
            popUpTo(nav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // ---------- Notificações ----------

    val context = LocalContext.current

    // Toque na notificação: vai direto para a aba Alertas (depois do login, se precisar).
    LaunchedEffect(abrirAlertas) {
        if (abrirAlertas) {
            irParaAba(Aba.ALERTAS.rota)
            onAlertasAbertos()
        }
    }

    // Android 13+: explica antes de pedir a permissão de notificação, uma vez só (RNF15).
    var pedirPermissao by remember { mutableStateOf(Notificacoes.precisaPedirPermissao(context)) }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) Notificacoes.verificarAgora(context)
    }
    if (pedirPermissao) {
        AlertDialog(
            onDismissRequest = {
                Notificacoes.marcarQuePerguntou(context)
                pedirPermissao = false
            },
            title = { Text("Ativar notificações?") },
            text = {
                Text(
                    "O Remédio Certo avisa quando um medicamento fica com estoque baixo " +
                        "ou perto de vencer, mesmo com o app fechado."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Notificacoes.marcarQuePerguntou(context)
                    pedirPermissao = false
                    permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Ativar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Notificacoes.marcarQuePerguntou(context)
                    pedirPermissao = false
                }) { Text("Agora não") }
            },
        )
    }

    // Depois do aviso de notificações: oferece entrar com biometria nas próximas vezes (RF02).
    if (oferecerBiometria && !pedirPermissao) {
        fun responder() {
            Biometria.marcarOferecida(context)
            onOfertaBiometriaRespondida()
        }
        AlertDialog(
            onDismissRequest = { responder() },
            title = { Text("Entrar com biometria?") },
            text = {
                Text(
                    "Da próxima vez, abra o app com a digital ou o rosto, sem digitar a senha. " +
                        "Dá para mudar depois em Mais → Perfil e preferências."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    responder()
                    Biometria.autenticar(
                        context = context,
                        titulo = "Ativar login por biometria",
                        onSucesso = { Biometria.definirAtivada(context, true) },
                        onUsarSenha = {},
                    )
                }) { Text("Ativar") }
            },
            dismissButton = { TextButton(onClick = { responder() }) { Text("Agora não") } },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {

                Aba.entries.forEach { aba ->

                    NavigationBarItem(
                        selected = rotaAtual == aba.rota,
                        onClick = {
                            irParaAba(aba.rota)
                        },
                        icon = {
                            Icon(
                                aba.icone,
                                contentDescription = aba.titulo
                            )
                        },
                        label = {
                            Text(aba.titulo)
                        }
                    )
                }
            }
        }
    ) { padding ->

        NavHost(
            navController = nav,
            startDestination = Aba.INICIO.rota,
            modifier = Modifier.padding(padding)
        ) {

            // ---------- Início ----------

            composable(Aba.INICIO.rota) {

                DashboardScreen(
                    usuario = usuario,

                    onVerHistorico = {
                        nav.navigate("historico")
                    },

                    onNovaMovimentacao = {
                        irParaAba(Aba.ESCANEAR.rota)
                    },

                    onVerAlertas = {
                        irParaAba(Aba.ALERTAS.rota)
                    }
                )
            }

            // ---------- Estoque ----------

            composable(Aba.ESTOQUE.rota) {
                EstoqueScreen(
                    onAbrirMedicamento = { medicamentoId, unidadeId ->
                        nav.navigate("medicamento-detalhe/$medicamentoId/$unidadeId")
                    }
                )
            }

            // ---------- Detalhe do medicamento (T07) ----------

            composable(
                "medicamento-detalhe/{medicamentoId}/{unidadeId}",
                arguments = listOf(
                    navArgument("medicamentoId") { type = NavType.IntType },
                    navArgument("unidadeId") { type = NavType.IntType },
                )
            ) { e ->

                MedicamentoDetalheScreen(
                    medicamentoId = e.arguments?.getInt("medicamentoId") ?: 0,
                    unidadeId = e.arguments?.getInt("unidadeId") ?: 0,
                    isAdmin = usuario.tipo == "ADMIN",
                    onVoltar = {
                        nav.popBackStack()
                    },
                    onEditar = { id ->
                        nav.navigate("medicamento/$id")
                    }
                )
            }

            // ---------- Escanear ----------

            composable(Aba.ESCANEAR.rota) {

                NovaMovimentacaoScreen(
                    usuario,
                    abrirScanner = true,
                    // Código não cadastrado: o administrador cadastra na hora (RF09 / Fluxo D)
                    onCadastrarMedicamento = { codigo ->
                        nav.navigate("medicamento-novo?codigo=${Uri.encode(codigo)}")
                    }
                )
            }

            // ---------- Alertas ----------

            composable(Aba.ALERTAS.rota) {
                AlertasScreen(
                    onRegistrarMovimentacao = {
                        irParaAba(Aba.ESCANEAR.rota)
                    }
                )
            }

            // ---------- Menu Mais ----------

            composable(Aba.MAIS.rota) {

                MaisScreen(
                    usuario = usuario,
                    onSair = onSair,

                    onHistorico = {
                        nav.navigate("historico")
                    },

                    onMedicamentos = {
                        nav.navigate("medicamentos")
                    },

                    onUnidades = {
                        nav.navigate("unidades")
                    },

                    onUsuarios = {
                        nav.navigate("usuarios")
                    },

                    onPerfil = {
                        nav.navigate("perfil")
                    },

                    onConfigAlertas = {
                        nav.navigate("config-alertas")
                    },

                    onGerarRelatorios = {
                        nav.navigate("relatorios")
                    }
                )
            }

            // ---------- Configuração de alertas (Administrador) ----------

            composable("config-alertas") {

                ConfiguracaoAlertasScreen(
                    onVoltar = {
                        nav.popBackStack()
                    }
                )
            }

            // ---------- Histórico ----------

            composable("historico") {

                HistoricoScreen(
                    onVoltar = {
                        nav.popBackStack()
                    }
                )
            }

            // ---------- Perfil e preferências (T18) ----------

            composable("perfil") {

                PerfilScreen(
                    usuario = usuario,
                    onVoltar = {
                        nav.popBackStack()
                    },
                    onTrocarSenha = {
                        nav.navigate("trocar-senha")
                    },
                    onSair = onSair
                )
            }

            // ---------- Trocar senha ----------

            composable("trocar-senha") {

                TrocarSenhaScreen(
                    onVoltar = {
                        nav.popBackStack()
                    }
                )
            }

            // ---------- Relatórios PDF ----------

            composable("relatorios") {

                RelatoriosScreen(
                    onVoltar = {
                        nav.popBackStack()
                    }
                )
            }

            // ---------- Cadastro de medicamentos ----------

            composable("medicamentos") {

                MedicamentosScreen(
                    onVoltar = {
                        nav.popBackStack()
                    },

                    onNovo = {
                        nav.navigate("medicamento-novo")
                    },

                    onEditar = { id ->
                        nav.navigate("medicamento/$id")
                    }
                )
            }

            composable(
                "medicamento-novo?codigo={codigo}",
                arguments = listOf(
                    navArgument("codigo") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { e ->

                MedicamentoFormScreen(
                    id = null,
                    codigoInicial = e.arguments?.getString("codigo"),
                    onConcluido = {
                        nav.popBackStack()
                    }
                )
            }

            composable(
                "medicamento/{id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.IntType
                    }
                )
            ) { e ->

                MedicamentoFormScreen(
                    id = e.arguments?.getInt("id"),
                    onConcluido = {
                        nav.popBackStack()
                    }
                )
            }

            // ---------- Cadastro de usuários ----------

            composable("usuarios") {

                UsuariosScreen(
                    onVoltar = {
                        nav.popBackStack()
                    },

                    onNovo = {
                        nav.navigate("usuario-novo")
                    },

                    onEditar = { id ->
                        nav.navigate("usuario/$id")
                    }
                )
            }

            composable("usuario-novo") {

                UsuarioFormScreen(
                    id = null,
                    usuarioLogadoId = usuario.id,
                    onConcluido = {
                        nav.popBackStack()
                    }
                )
            }

            composable(
                "usuario/{id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.IntType
                    }
                )
            ) { e ->

                UsuarioFormScreen(
                    id = e.arguments?.getInt("id"),
                    usuarioLogadoId = usuario.id,
                    onConcluido = {
                        nav.popBackStack()
                    }
                )
            }

            // ---------- Cadastro de unidades ----------

            composable("unidades") {

                UnidadesScreen(
                    onVoltar = {
                        nav.popBackStack()
                    },

                    onNova = {
                        nav.navigate("unidade-nova")
                    },

                    onEditar = { id ->
                        nav.navigate("unidade/$id")
                    }
                )
            }

            composable("unidade-nova") {

                UnidadeFormScreen(
                    id = null,
                    onConcluido = {
                        nav.popBackStack()
                    }
                )
            }

            composable(
                "unidade/{id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.IntType
                    }
                )
            ) { e ->

                UnidadeFormScreen(
                    id = e.arguments?.getInt("id"),
                    onConcluido = {
                        nav.popBackStack()
                    }
                )
            }
        }
    }
}

// ---------- Tela provisória ----------

@Composable
fun TelaProvisoria(
    titulo: String,
    subtitulo: String = "Em construção"
) {

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            titulo,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(8.dp))

        Text(subtitulo)
    }
}

// ---------- Tela Mais ----------

@Composable
fun MaisScreen(
    usuario: UsuarioDto,
    onSair: () -> Unit,
    onHistorico: () -> Unit,
    onMedicamentos: () -> Unit,
    onUnidades: () -> Unit,
    onUsuarios: () -> Unit,
    onPerfil: () -> Unit,
    onConfigAlertas: () -> Unit,
    onGerarRelatorios: () -> Unit
) {

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            usuario.nome,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(usuario.email)

        Text("Perfil: ${usuario.tipo}")

        Spacer(Modifier.height(12.dp))

        // Histórico

        OutlinedButton(
            onClick = onHistorico,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Histórico de movimentações")
        }

        // Perfil e preferências: biometria, notificações, trocar senha (T18)

        OutlinedButton(
            onClick = onPerfil,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Perfil e preferências")
        }

        // Gerar relatórios PDF

        OutlinedButton(
            onClick = onGerarRelatorios,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gerar Relatórios PDF")
        }

        // Cadastros administrativos

        if (usuario.tipo == "ADMIN") {

            Text(
                "Cadastros",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
                onClick = onMedicamentos,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Medicamentos")
            }

            OutlinedButton(
                onClick = onUnidades,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unidades de saúde")
            }

            OutlinedButton(
                onClick = onUsuarios,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Usuários")
            }

            Text(
                "Alertas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
                onClick = onConfigAlertas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configuração de alertas")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Sair

        OutlinedButton(
            onClick = onSair
        ) {
            Text("Sair")
        }
    }
}