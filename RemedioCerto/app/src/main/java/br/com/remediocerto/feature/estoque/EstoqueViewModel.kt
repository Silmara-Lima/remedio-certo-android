package br.com.remediocerto.feature.estoque

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.ui.StatusEstoque
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

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
    data class Sucesso(val itens: List<ItemEstoque>) : EstoqueUiState
    data class Erro(val mensagem: String) : EstoqueUiState
}

class EstoqueViewModel : ViewModel() {
    var estado by mutableStateOf<EstoqueUiState>(EstoqueUiState.Carregando); private set
    var atualizando by mutableStateOf(false); private set
    var busca by mutableStateOf("")
    var filtro by mutableStateOf<StatusEstoque?>(null)

    init { carregar() }

    fun carregar(puxouParaAtualizar: Boolean = false) = viewModelScope.launch {
        if (puxouParaAtualizar) atualizando = true else estado = EstoqueUiState.Carregando
        estado = try {
            EstoqueUiState.Sucesso(agrupar(ApiClient.api.listarEstoque()))
        } catch (e: HttpException) {
            EstoqueUiState.Erro(
                if (e.code() == 401) "Sessão expirada. Toque em Sair (aba Mais) e entre novamente."
                else "Erro ${e.code()} ao carregar o estoque."
            )
        } catch (e: IOException) {
            EstoqueUiState.Erro("Não foi possível conectar à API.")
        }
        atualizando = false
    }

    fun filtrar(itens: List<ItemEstoque>) = itens.filter { item ->
        (filtro == null || item.status == filtro) &&
                (busca.isBlank() || item.nome.contains(busca.trim(), ignoreCase = true))
    }

    // A API devolve um registro por lote; aqui agrupamos por medicamento + unidade.
    private fun agrupar(lotes: List<EstoqueDto>): List<ItemEstoque> =
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
}