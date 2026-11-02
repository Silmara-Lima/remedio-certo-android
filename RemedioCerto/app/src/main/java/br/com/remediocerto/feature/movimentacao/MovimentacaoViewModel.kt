package br.com.remediocerto.feature.movimentacao

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.remediocerto.core.database.DatabaseProvider
import br.com.remediocerto.core.database.entity.MovimentacaoPendenteEntity
import br.com.remediocerto.core.network.*
import br.com.remediocerto.core.offline.Sincronizacao
import br.com.remediocerto.core.offline.medicamentosDoCache
import br.com.remediocerto.core.offline.paraDto
import br.com.remediocerto.core.offline.paraLocal
import br.com.remediocerto.core.offline.saldoDepois
import br.com.remediocerto.core.offline.unidadesDoCache
import br.com.remediocerto.core.ui.dataDigitosParaIso
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

enum class TipoMov(val api: String, val rotulo: String) {
    SAIDA("saida", "Saída"),
    ENTRADA("entrada", "Entrada"),
    DESCARTE("descarte", "Descarte"),
    AJUSTE("ajuste", "Ajuste"),
}

class MovimentacaoViewModel(
    private val usuario: UsuarioDto,
    app: Application,
) : AndroidViewModel(app) {
    val isAdmin = usuario.tipo == "ADMIN"
    var jaAbriuScanner = false

    private val banco = DatabaseProvider.getDatabase(app)
    private val estoqueLocal = banco.estoqueLocalDao()
    private val pendentes = banco.movimentacaoPendenteDao()
    private val json = Json { ignoreUnknownKeys = true }

    var tipo by mutableStateOf(TipoMov.SAIDA); private set
    var busca by mutableStateOf(""); private set
    var sugestoes by mutableStateOf<List<MedicamentoDto>>(emptyList()); private set
    var medicamento by mutableStateOf<MedicamentoDto?>(null); private set
    var unidades by mutableStateOf<List<UnidadeDto>>(emptyList()); private set
    var unidadeId by mutableStateOf(usuario.unidadeSaudeId); private set
    private var lotes by mutableStateOf<List<EstoqueDto>>(emptyList())
    var loteSelecionado by mutableStateOf<EstoqueDto?>(null)

    var novoLote by mutableStateOf("")
    var validade by mutableStateOf("")          // só dígitos, ex.: "31122027" (a tela mostra 31/12/2027)
    var quantidade by mutableStateOf("")
    var documento by mutableStateOf("")
    var observacao by mutableStateOf("")

    var enviando by mutableStateOf(false); private set
    var mensagem by mutableStateOf<String?>(null); private set
    var erro by mutableStateOf<String?>(null)

    /** true quando os dados vieram do estoque salvo no aparelho (sem internet). */
    var semConexao by mutableStateOf(false); private set

    private var buscaJob: Job? = null

    init {
        if (isAdmin) viewModelScope.launch {
            unidades = try {
                ApiClient.api.listarUnidades()
            } catch (e: IOException) {
                unidadesDoCache(estoqueLocal.listarTodos())
            } catch (e: HttpException) {
                emptyList()
            }
        }
    }

    // ---------- medicamento ----------
    fun onBuscaChange(texto: String) {
        busca = texto
        buscaJob?.cancel()
        if (texto.trim().length < 2) { sugestoes = emptyList(); return }
        buscaJob = viewModelScope.launch {
            delay(300)
            sugestoes = buscarMedicamentos(texto.trim())
        }
    }

    fun processarCodigo(codigo: String) = viewModelScope.launch {
        erro = null
        val achados = buscarMedicamentos(codigo)
        val m = achados.firstOrNull { it.codigoBarras == codigo } ?: achados.firstOrNull()
        if (m != null) selecionarMedicamento(m)
        else erro = "Nenhum medicamento cadastrado com o código $codigo."
    }

    // Na API; sem internet, no estoque salvo no aparelho.
    private suspend fun buscarMedicamentos(texto: String): List<MedicamentoDto> = try {
        ApiClient.api.buscarMedicamentos(texto)
    } catch (e: IOException) {
        semConexao = true
        medicamentosDoCache(estoqueLocal.buscar(texto))
    } catch (e: HttpException) {
        emptyList()
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

    fun lotesDisponiveis(): List<EstoqueDto> = lotesDisponiveisPara(tipo, lotes, unidadeId)

    private fun ajustarLote() {
        val disponiveis = lotesDisponiveis()
        if (loteSelecionado !in disponiveis) loteSelecionado = disponiveis.firstOrNull()   // FEFO
    }

    private fun carregarLotes() {
        val m = medicamento ?: return
        viewModelScope.launch {
            lotes = try {
                val daApi = ApiClient.api.listarEstoque(m.id)
                // Guarda a cópia mais recente dos lotes deste medicamento.
                runCatching {
                    estoqueLocal.limparMedicamento(m.id)
                    estoqueLocal.salvarEstoque(daApi.map { it.paraLocal(System.currentTimeMillis()) })
                }
                semConexao = false
                daApi
            } catch (e: IOException) {
                semConexao = true
                estoqueLocal.listarPorMedicamento(m.id).map { it.paraDto(LocalDate.now()) }
            } catch (e: HttpException) {
                emptyList()
            }
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
            val validadeIso = dataDigitosParaIso(validade)
            if (novoLote.isBlank()) { erro = "Informe o número do lote."; return }
            if (validadeIso == null) { erro = "Informe uma validade válida (dd/mm/aaaa)."; return }
            if (LocalDate.parse(validadeIso).isBefore(LocalDate.now())) {
                erro = "A validade não pode ser uma data passada."; return
            }
            MovimentacaoRequest(
                tipo.api, m.id, unidade, novoLote.trim(), validadeIso, qtd,
                numeroDocumento = documento.ifBlank { null },
                observacao = observacao.ifBlank { null },
            )
        } else {
            val lote = loteSelecionado
            if (lote == null) { erro = "Selecione o lote."; return }
            validarMovimentacaoEmLote(tipo, qtd, lote, observacao)?.let { erro = it; return }
            MovimentacaoRequest(
                tipo.api, m.id, unidade, lote.lote, lote.validade, qtd,
                estoqueLoteId = lote.id,
                numeroDocumento = documento.ifBlank { null },
                observacao = observacao.ifBlank { null },
            )
        }

        // Toda movimentação ganha um id_local. Se a internet cair no meio do envio,
        // o reenvio usa o mesmo id e a API não aplica a movimentação duas vezes.
        val idLocal = UUID.randomUUID().toString()
        val payload = json.encodeToString(req)
        val descricao = "${tipo.rotulo} de $qtd un. de ${m.nome}"

        viewModelScope.launch {
            enviando = true
            try {
                val resultado = ApiClient.api.sincronizarMovimentacoes(
                    LoteMovimentacoesRequest(listOf(Sincronizacao.itemDoLote(idLocal, payload)))
                ).resultados.firstOrNull()
                if (resultado?.status == "aceito") {
                    mensagem = "$descricao registrada com sucesso."
                    limparCampos()
                    carregarLotes()
                } else {
                    erro = resultado?.mensagem ?: "A API recusou a movimentação."
                }
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)
            } catch (e: IOException) {
                guardarParaEnviarDepois(idLocal, payload, req, descricao)
                mensagem = "Sem internet: $descricao ficou salva no aparelho e será enviada quando a conexão voltar."
                limparCampos()
                carregarLotes()
            }
            enviando = false
        }
    }

    private suspend fun guardarParaEnviarDepois(
        idLocal: String,
        payload: String,
        req: MovimentacaoRequest,
        descricao: String,
    ) {
        pendentes.inserir(
            MovimentacaoPendenteEntity(
                idLocal = idLocal,
                usuarioId = usuario.id,
                payload = payload,
                descricao = descricao,
                criadaEm = System.currentTimeMillis(),
            )
        )
        // Atualiza o saldo salvo, para as próximas validações offline (RN01) usarem o valor certo.
        req.estoqueLoteId?.let { id ->
            estoqueLocal.buscarPorChave(id.toString())?.let { local ->
                estoqueLocal.salvarEstoque(
                    listOf(local.copy(quantidade = saldoDepois(req.tipo, local.quantidade, req.quantidade)))
                )
            }
        }
        Sincronizacao.agendar(getApplication())
    }

    private fun limparCampos() {
        quantidade = ""; documento = ""; observacao = ""; novoLote = ""; validade = ""
    }
}
