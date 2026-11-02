package br.com.remediocerto.feature.alertas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.mensagemDeErro
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface AlertasUiState {
    data object Carregando : AlertasUiState
    data class Sucesso(val alertas: List<AlertaDto>) : AlertasUiState
    data class Erro(val mensagem: String) : AlertasUiState
}

/** Ação que o usuário pediu e que ainda espera confirmação na caixa de diálogo. */
data class AcaoPendente(val alerta: AlertaDto, val resolver: Boolean)

class AlertasViewModel : ViewModel() {
    var estado by mutableStateOf<AlertasUiState>(AlertasUiState.Carregando); private set
    var atualizando by mutableStateOf(false); private set
    var aba by mutableStateOf(AbaAlerta.ATIVOS)
    var acaoPendente by mutableStateOf<AcaoPendente?>(null)
    var aviso by mutableStateOf<String?>(null)
    var enviando by mutableStateOf(false); private set

    // Busca todos os alertas de uma vez e separa por aba no aparelho,
    // assim cada aba já mostra a contagem.
    fun carregar(puxouParaAtualizar: Boolean = false) = viewModelScope.launch {
        if (puxouParaAtualizar) atualizando = true else estado = AlertasUiState.Carregando
        estado = try {
            // Pede para a API criar os alertas novos antes de listar.
            // Se falhar, segue com a lista que já existe.
            runCatching { ApiClient.api.gerarAlertas() }
            AlertasUiState.Sucesso(ApiClient.api.listarAlertas())
        } catch (e: HttpException) {
            AlertasUiState.Erro(mensagemDeErro(e))
        } catch (e: IOException) {
            AlertasUiState.Erro("Não foi possível conectar à API.")
        }
        atualizando = false
    }

    fun confirmarAcao() {
        val pendente = acaoPendente ?: return
        acaoPendente = null
        viewModelScope.launch {
            enviando = true
            try {
                if (pendente.resolver) ApiClient.api.resolverAlerta(pendente.alerta.id)
                else ApiClient.api.ignorarAlerta(pendente.alerta.id)
                aviso = if (pendente.resolver) "Alerta marcado como resolvido." else "Alerta ignorado."
                carregar(puxouParaAtualizar = true)
            } catch (e: HttpException) {
                aviso = mensagemDeErro(e)
            } catch (e: IOException) {
                aviso = "Sem conexão. Tente novamente."
            }
            enviando = false
        }
    }
}
