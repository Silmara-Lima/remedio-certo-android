package br.com.remediocerto.feature.movimentacao

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.remediocerto.core.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class TipoMov(val api: String, val rotulo: String) {
    SAIDA("saida", "Saída"),
    ENTRADA("entrada", "Entrada"),
    DESCARTE("descarte", "Descarte"),
    AJUSTE("ajuste", "Ajuste"),
}

class MovimentacaoViewModel(private val usuario: UsuarioDto) : ViewModel() {
    val isAdmin = usuario.tipo == "ADMIN"
    var jaAbriuScanner = false

    var tipo by mutableStateOf(TipoMov.SAIDA); private set
    var busca by mutableStateOf(""); private set
    var sugestoes by mutableStateOf<List<MedicamentoDto>>(emptyList()); private set
    var medicamento by mutableStateOf<MedicamentoDto?>(null); private set
    var unidades by mutableStateOf<List<UnidadeDto>>(emptyList()); private set
    var unidadeId by mutableStateOf(usuario.unidadeSaudeId); private set
    private var lotes by mutableStateOf<List<EstoqueDto>>(emptyList())
    var loteSelecionado by mutableStateOf<EstoqueDto?>(null)

    var novoLote by mutableStateOf("")
    var validade by mutableStateOf("")          // dd/mm/aaaa (somente na entrada)
    var quantidade by mutableStateOf("")
    var documento by mutableStateOf("")
    var observacao by mutableStateOf("")

    var enviando by mutableStateOf(false); private set
    var mensagem by mutableStateOf<String?>(null); private set
    var erro by mutableStateOf<String?>(null)

    private var buscaJob: Job? = null

    init {
        if (isAdmin) viewModelScope.launch {
            unidades = runCatching { ApiClient.api.listarUnidades() }.getOrDefault(emptyList())
        }
    }

    // ---------- medicamento ----------
    fun onBuscaChange(texto: String) {
        busca = texto
        buscaJob?.cancel()
        if (texto.trim().length < 2) { sugestoes = emptyList(); return }
        buscaJob = viewModelScope.launch {
            delay(300)                                  // espera o usuário parar de digitar
            sugestoes = runCatching { ApiClient.api.buscarMedicamentos(texto.trim()) }.getOrDefault(emptyList())
        }
    }

    fun processarCodigo(codigo: String) = viewModelScope.launch {
        erro = null
        val achados = runCatching { ApiClient.api.buscarMedicamentos(codigo) }.getOrDefault(emptyList())
        val m = achados.firstOrNull { it.codigoBarras == codigo } ?: achados.firstOrNull()
        if (m != null) selecionarMedicamento(m)
        else erro = "Nenhum medicamento cadastrado com o código $codigo."
    }

    fun selecionarMedicamento(m: MedicamentoDto) {
        medicamento = m; busca = ""; sugestoes = emptyList(); mensagem = null
        carregarLotes()
    }

    fun trocarMedicamento() {
        medicamento = null; lotes = emptyList(); loteSelecionado = null
    }

    // ---------- tipo, unidade e lote ----------
    fun selecionarTipo(t: TipoMov) { tipo = t; mensagem = null; erro = null; ajustarLote() }

    fun selecionarUnidade(id: Int) { unidadeId = id; ajustarLote() }

    fun lotesDisponiveis(): List<EstoqueDto> = lotes
        .filter { it.unidadeId == unidadeId }
        .filter {
            when (tipo) {
                TipoMov.SAIDA -> it.quantidade > 0 && it.status != "vencido"   // RN03
                TipoMov.DESCARTE -> it.quantidade > 0
                else -> true
            }
        }

    private fun ajustarLote() {
        val disponiveis = lotesDisponiveis()
        if (loteSelecionado !in disponiveis) loteSelecionado = disponiveis.firstOrNull()   // FEFO
    }

    private fun carregarLotes() {
        val m = medicamento ?: return
        viewModelScope.launch {
            lotes = runCatching { ApiClient.api.listarEstoque(m.id) }.getOrDefault(emptyList())
                .sortedBy { it.validade }
            ajustarLote()
        }
    }

    // ---------- confirmação ----------
    fun confirmar() {
        erro = null; mensagem = null
        val m = medicamento
        val unidade = unidadeId
        val qtd = quantidade.trim().toIntOrNull()
        if (m == null) { erro = "Selecione o medicamento."; return }
        if (unidade == null) { erro = "Selecione a unidade."; return }
        if (qtd == null || qtd < 0 || (qtd == 0 && tipo != TipoMov.AJUSTE)) { erro = "Informe uma quantidade válida."; return }

        val req = if (tipo == TipoMov.ENTRADA) {
            val validadeIso = paraIso(validade)
            if (novoLote.isBlank()) { erro = "Informe o número do lote."; return }
            if (validadeIso == null) { erro = "Informe a validade no formato dd/mm/aaaa."; return }
            MovimentacaoRequest(tipo.api, m.id, unidade, novoLote.trim(), validadeIso, qtd,
                numeroDocumento = documento.ifBlank { null }, observacao = observacao.ifBlank { null })
        } else {
            val lote = loteSelecionado
            if (lote == null) { erro = "Selecione o lote."; return }
            if (tipo != TipoMov.AJUSTE && qtd > lote.quantidade) {                 // RN01
                erro = "Saldo insuficiente: o lote ${lote.lote} tem ${lote.quantidade} un."; return
            }
            if (tipo == TipoMov.AJUSTE && observacao.isBlank()) {                 // RN06
                erro = "Informe a justificativa do ajuste no campo Observação."; return
            }
            MovimentacaoRequest(tipo.api, m.id, unidade, lote.lote, lote.validade, qtd,
                estoqueLoteId = lote.id,
                numeroDocumento = documento.ifBlank { null }, observacao = observacao.ifBlank { null })
        }

        viewModelScope.launch {
            enviando = true
            try {
                ApiClient.api.registrarMovimentacao(req)
                mensagem = "${tipo.rotulo} de $qtd un. de ${m.nome} registrada com sucesso."
                quantidade = ""; documento = ""; observacao = ""; novoLote = ""; validade = ""
                carregarLotes()                                  // atualiza os saldos dos lotes
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)
            } catch (e: IOException) {
                erro = "Não foi possível conectar à API."
            }
            enviando = false
        }
    }

    private fun paraIso(br: String): String? = runCatching {
        LocalDate.parse(br.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString()
    }.getOrNull()
}