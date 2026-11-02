package br.com.remediocerto.feature.estoque

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.remediocerto.core.database.DatabaseProvider
import br.com.remediocerto.core.database.entity.MovimentacaoPendenteEntity
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.offline.Sincronizacao
import br.com.remediocerto.core.offline.paraDto
import br.com.remediocerto.core.offline.paraLocal
import br.com.remediocerto.core.ui.StatusEstoque
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

data class ItemEstoque(
    val chave: String,
    val nome: String,
    val unidade: String?,
    val total: Int,
    val minimo: Int,
    val status: StatusEstoque,
    val lotes: List<EstoqueDto>,
)

sealed interface EstoqueUiState {
    data object Carregando : EstoqueUiState
    /** salvoEm != null: sem internet, mostrando a cópia salva no aparelho nesse horário. */
    data class Sucesso(val itens: List<ItemEstoque>, val salvoEm: Long? = null) : EstoqueUiState
    data class Erro(val mensagem: String) : EstoqueUiState
}

class EstoqueViewModel(app: Application) : AndroidViewModel(app) {
    private val banco = DatabaseProvider.getDatabase(app)
    private val estoqueLocal = banco.estoqueLocalDao()
    private val filaOffline = banco.movimentacaoPendenteDao()

    /** Movimentações feitas sem internet que ainda não foram enviadas. */
    val pendentes: StateFlow<Int> = filaOffline.contarPendentes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Movimentações offline que a API recusou, com o motivo. */
    val rejeitadas: StateFlow<List<MovimentacaoPendenteEntity>> = filaOffline.rejeitadas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var estado by mutableStateOf<EstoqueUiState>(EstoqueUiState.Carregando); private set
    var atualizando by mutableStateOf(false); private set
    var busca by mutableStateOf("")
    var filtro by mutableStateOf<StatusEstoque?>(null)

    // O carregamento é disparado pela tela (LaunchedEffect), sempre que a aba é aberta.
    fun carregar(puxouParaAtualizar: Boolean = false) = viewModelScope.launch {
        if (puxouParaAtualizar) atualizando = true else estado = EstoqueUiState.Carregando
        estado = try {
            val lotes = ApiClient.api.listarEstoque()
            // Guarda a cópia para usar sem internet e aproveita para enviar a fila offline.
            runCatching {
                estoqueLocal.limparEstoque()
                estoqueLocal.salvarEstoque(lotes.map { it.paraLocal(System.currentTimeMillis()) })
            }
            Sincronizacao.agendar(getApplication())
            EstoqueUiState.Sucesso(agruparEstoque(lotes))
        } catch (e: HttpException) {
            EstoqueUiState.Erro(mensagemDeErro(e))
        } catch (e: IOException) {
            val salvos = estoqueLocal.listarTodos()
            if (salvos.isEmpty()) {
                EstoqueUiState.Erro("Sem conexão e ainda não há estoque salvo no aparelho.")
            } else {
                EstoqueUiState.Sucesso(
                    agruparEstoque(salvos.map { it.paraDto(LocalDate.now()) }),
                    salvoEm = salvos.minOf { it.atualizadoEm },
                )
            }
        }
        atualizando = false
    }

    fun enviarAgora() = Sincronizacao.agendar(getApplication())

    /** Tira da lista uma movimentação recusada (depois de o usuário ler o motivo). */
    fun dispensar(idLocal: String) = viewModelScope.launch { filaOffline.remover(idLocal) }

    fun filtrar(itens: List<ItemEstoque>) = itens.filter { item ->
        (filtro == null || item.status == filtro) &&
                (busca.isBlank() || item.nome.contains(busca.trim(), ignoreCase = true))
    }
}

// A API devolve um registro por lote; aqui agrupamos por medicamento + unidade.
// Fica fora do ViewModel para poder ser testada sem a API.
fun agruparEstoque(lotes: List<EstoqueDto>): List<ItemEstoque> =
    lotes.groupBy { "${it.medicamentoId}-${it.unidadeId}" }
        .map { (chave, grupo) ->
            val primeiro = grupo.first()
            val total = grupo.sumOf { it.quantidade }
            val minimo = primeiro.medicamento?.quantidadeMinima ?: 0
            val situacoes = grupo.map { StatusEstoque.de(it.status) }
            val status = when {
                StatusEstoque.VENCIDO in situacoes -> StatusEstoque.VENCIDO
                total <= minimo -> StatusEstoque.BAIXO
                StatusEstoque.VENCENDO in situacoes -> StatusEstoque.VENCENDO
                else -> StatusEstoque.NORMAL
            }
            ItemEstoque(
                chave = chave,
                nome = primeiro.medicamento?.nome ?: "Medicamento ${primeiro.medicamentoId}",
                unidade = primeiro.unidade?.nome,
                total = total,
                minimo = minimo,
                status = status,
                lotes = grupo.sortedBy { it.validade },   // FEFO: vence primeiro, aparece primeiro
            )
        }
        .sortedWith(compareByDescending<ItemEstoque> { it.status.gravidade }.thenBy { it.nome })