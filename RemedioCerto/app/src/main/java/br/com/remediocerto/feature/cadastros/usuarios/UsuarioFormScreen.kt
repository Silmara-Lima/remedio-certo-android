package br.com.remediocerto.feature.cadastros.usuarios

import android.util.Patterns
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.UnidadeDto
import br.com.remediocerto.core.network.UsuarioRequest
import br.com.remediocerto.core.network.mensagemDeErro
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class UsuarioFormViewModel(private val id: Int?) : ViewModel() {
    val edicao = id != null

    var nome by mutableStateOf("")
    var email by mutableStateOf("")
    var perfil by mutableStateOf("funcionario")
    var unidadeId by mutableStateOf<Int?>(null)
    var senha by mutableStateOf("")
    var ativo by mutableStateOf(true); private set
    var unidades by mutableStateOf<List<UnidadeDto>>(emptyList()); private set

    var carregando by mutableStateOf(true); private set
    var salvando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null)
    var concluido by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            try {
                unidades = ApiClient.api.listarUnidades()
                if (id != null) {
                    val u = ApiClient.api.buscarUsuario(id)
                    nome = u.nome; email = u.email; perfil = u.perfil
                    unidadeId = u.unidadeId; ativo = u.ativo
                }
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)
            } catch (e: IOException) {
                erro = "Não foi possível conectar à API."
            }
            carregando = false
        }
    }

    fun salvar(reativar: Boolean = false) {
        erro = null
        val emailLimpo = email.trim().lowercase()
        when {
            nome.trim().length < 2 -> { erro = "Informe o nome do usuário."; return }
            !Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches() -> { erro = "Informe um e-mail válido."; return }
            perfil == "funcionario" && unidadeId == null -> { erro = "Funcionário precisa estar vinculado a uma unidade."; return }
            !edicao && senha.length < 6 -> { erro = "A senha inicial deve ter pelo menos 6 caracteres."; return }
            edicao && senha.isNotEmpty() && senha.length < 6 -> { erro = "A nova senha deve ter pelo menos 6 caracteres."; return }
        }
        val req = UsuarioRequest(
            nome = nome.trim(),
            email = emailLimpo,
            perfil = perfil,
            ativo = if (reativar) true else ativo,
            unidadeId = unidadeId,
            senha = senha.ifEmpty { null },              // na edição, vazio = manter a senha atual
        )
        executar { if (id == null) ApiClient.api.criarUsuario(req) else ApiClient.api.atualizarUsuario(id, req) }
    }

    fun desativar() {
        val id = id ?: return
        executar { ApiClient.api.desativarUsuario(id) }
    }

    private fun executar(acao: suspend () -> Unit) = viewModelScope.launch {
        salvando = true
        try {
            acao(); concluido = true
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        salvando = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioFormScreen(id: Int?, usuarioLogadoId: Int, onConcluido: () -> Unit) {
    val vm: UsuarioFormViewModel = viewModel(factory = viewModelFactory { initializer { UsuarioFormViewModel(id) } })
    var confirmarDesativar by remember { mutableStateOf(false) }
    var mostrarSenha by remember { mutableStateOf(false) }
    val ehVoceMesmo = id == usuarioLogadoId

    LaunchedEffect(vm.concluido) { if (vm.concluido) onConcluido() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.edicao) "Editar usuário" else "Novo usuário") },
                navigationIcon = {
                    IconButton(onClick = onConcluido) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (vm.carregando) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.ativo) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Este usuário está inativo e não consegue entrar no app.", Modifier.weight(1f))
                        TextButton(onClick = { vm.salvar(reativar = true) }) { Text("Reativar") }
                    }
                }
            }

            OutlinedTextField(vm.nome, { vm.nome = it }, label = { Text("Nome *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                vm.email, { vm.email = it }, label = { Text("E-mail *") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Perfil", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.perfil == "funcionario", onClick = { vm.perfil = "funcionario" },
                    label = { Text("Funcionário") }, enabled = !ehVoceMesmo)
                FilterChip(selected = vm.perfil == "admin", onClick = { vm.perfil = "admin" },
                    label = { Text("Administrador") }, enabled = !ehVoceMesmo)
            }
            if (ehVoceMesmo) {
                Text("Você não pode alterar o seu próprio perfil.", style = MaterialTheme.typography.bodySmall)
            }

            Text(
                if (vm.perfil == "funcionario") "Unidade *" else "Unidade (opcional para administrador)",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.perfil == "admin") {
                    FilterChip(selected = vm.unidadeId == null, onClick = { vm.unidadeId = null }, label = { Text("Nenhuma") })
                }
                vm.unidades.forEach { u ->
                    FilterChip(selected = vm.unidadeId == u.id, onClick = { vm.unidadeId = u.id }, label = { Text(u.nome) })
                }
            }

            OutlinedTextField(
                value = vm.senha,
                onValueChange = { vm.senha = it },
                label = { Text(if (vm.edicao) "Nova senha (deixe em branco para manter)" else "Senha inicial *") },
                singleLine = true,
                visualTransformation = if (mostrarSenha) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { mostrarSenha = !mostrarSenha }) { Text(if (mostrarSenha) "Ocultar" else "Mostrar") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!vm.edicao) {
                Text("Informe esta senha ao usuário. Ele poderá trocá-la depois no app.",
                    style = MaterialTheme.typography.bodySmall)
            }

            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { vm.salvar() }, enabled = !vm.salvando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.salvando) "Salvando..." else "Salvar")
            }
            if (vm.edicao && vm.ativo && !ehVoceMesmo) {
                OutlinedButton(
                    onClick = { confirmarDesativar = true },
                    enabled = !vm.salvando,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Desativar usuário") }
            }
        }
    }

    if (confirmarDesativar) {
        AlertDialog(
            onDismissRequest = { confirmarDesativar = false },
            title = { Text("Desativar usuário?") },
            text = { Text("O usuário não conseguirá mais entrar no app. As movimentações que ele registrou continuam no histórico, e é possível reativá-lo depois.") },
            confirmButton = { TextButton(onClick = { confirmarDesativar = false; vm.desativar() }) { Text("Desativar") } },
            dismissButton = { TextButton(onClick = { confirmarDesativar = false }) { Text("Cancelar") } },
        )
    }
}