package br.com.remediocerto.feature.alertas.configuracao

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.ConfiguracaoAlertaDto
import br.com.remediocerto.core.network.ConfiguracaoAlertaRequest
import br.com.remediocerto.core.network.UnidadeDto
import br.com.remediocerto.core.network.mensagemDeErro
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ConfiguracaoAlertasViewModel : ViewModel() {
    var unidades by mutableStateOf<List<UnidadeDto>>(emptyList()); private set
    private var configuracoes = emptyList<ConfiguracaoAlertaDto>()

    var unidadeSelecionada by mutableStateOf<UnidadeDto?>(null); private set
    var estoqueMinimo by mutableStateOf("")
    var diasVencimento by mutableStateOf("")
    var diasCritico by mutableStateOf("")
    var notificacaoPush by mutableStateOf(true)
    var notificacaoEmail by mutableStateOf(false)

    var carregando by mutableStateOf(true); private set
    var salvando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null)
    var aviso by mutableStateOf<String?>(null)

    init { carregar() }

    fun carregar() = viewModelScope.launch {
        carregando = true
        erro = null
        try {
            unidades = ApiClient.api.listarUnidades()
            configuracoes = ApiClient.api.listarConfiguracoesAlerta()
            (unidadeSelecionada ?: unidades.firstOrNull())?.let { selecionar(it) }
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }

    /** Preenche o formulário com a configuração da unidade, ou com os padrões do plano se ela ainda não tiver. */
    fun selecionar(unidade: UnidadeDto) {
        unidadeSelecionada = unidade
        erro = null
        val c = configuracoes.firstOrNull { it.unidadeId == unidade.id }
        estoqueMinimo = (c?.estoqueMinimoPadrao ?: 10).toString()
        diasVencimento = (c?.diasAlertaVencimento ?: 30).toString()
        diasCritico = (c?.diasAlertaCritico ?: 7).toString()
        notificacaoPush = c?.notificacaoPush ?: true
        notificacaoEmail = c?.notificacaoEmail ?: false
    }

    fun salvar() {
        val unidade = unidadeSelecionada ?: return
        erro = null
        val valores = when (val r = validarConfiguracao(estoqueMinimo, diasVencimento, diasCritico)) {
            is ResultadoValidacao.Erro -> { erro = r.mensagem; return }
            is ResultadoValidacao.Ok -> r.valores
        }
        val req = ConfiguracaoAlertaRequest(
            unidadeId = unidade.id,
            estoqueMinimoPadrao = valores.estoqueMinimo,
            diasAlertaVencimento = valores.diasVencimento,
            diasAlertaCritico = valores.diasCritico,
            notificacaoEmail = notificacaoEmail,
            notificacaoPush = notificacaoPush,
        )
        viewModelScope.launch {
            salvando = true
            try {
                val existente = configuracoes.firstOrNull { it.unidadeId == unidade.id }
                val salva = if (existente == null) ApiClient.api.criarConfiguracaoAlerta(req)
                            else ApiClient.api.atualizarConfiguracaoAlerta(existente.id, req)
                configuracoes = configuracoes.filterNot { it.unidadeId == unidade.id } + salva
                aviso = "Configuração de ${unidade.nome} salva."
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)
            } catch (e: IOException) {
                erro = "Não foi possível conectar à API."
            }
            salvando = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracaoAlertasScreen(onVoltar: () -> Unit, vm: ConfiguracaoAlertasViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.aviso) { vm.aviso?.let { snackbar.showSnackbar(it); vm.aviso = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuração de alertas") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                windowInsets = WindowInsets(0),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (vm.carregando) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (vm.unidades.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(vm.erro ?: "Nenhuma unidade cadastrada.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.carregar() }) { Text("Tentar novamente") }
            }
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Unidade", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.unidades.forEach { u ->
                    FilterChip(
                        selected = vm.unidadeSelecionada?.id == u.id,
                        onClick = { vm.selecionar(u) },
                        label = { Text(u.nome) },
                    )
                }
            }

            Text("Limites", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            CampoNumero(
                "Estoque mínimo padrão (unidades)", vm.estoqueMinimo,
                "Usado quando o medicamento não tem mínimo próprio.",
            ) { vm.estoqueMinimo = it }
            CampoNumero(
                "Avisar vencimento com quantos dias", vm.diasVencimento,
                "Padrão: 30 dias antes da validade.",
            ) { vm.diasVencimento = it }
            CampoNumero(
                "Alerta crítico com quantos dias", vm.diasCritico,
                "Padrão: 7 dias. Precisa ser menor que o aviso de vencimento.",
            ) { vm.diasCritico = it }

            Text("Canais", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Interruptor("Notificação push no celular", vm.notificacaoPush) { vm.notificacaoPush = it }
            Interruptor("Notificação por e-mail", vm.notificacaoEmail) { vm.notificacaoEmail = it }

            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { vm.salvar() }, enabled = !vm.salvando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.salvando) "Salvando..." else "Salvar")
            }
        }
    }
}

@Composable
private fun CampoNumero(rotulo: String, valor: String, ajuda: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { novo -> onChange(novo.filter(Char::isDigit).take(4)) },
        label = { Text(rotulo) },
        supportingText = { Text(ajuda) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Interruptor(rotulo: String, marcado: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, Modifier.weight(1f))
        Switch(checked = marcado, onCheckedChange = onChange)
    }
}
