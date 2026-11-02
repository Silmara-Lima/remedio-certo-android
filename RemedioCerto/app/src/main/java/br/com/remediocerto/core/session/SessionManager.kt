package br.com.remediocerto.core.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.remediocerto.core.network.UsuarioDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "sessao")

class SessionManager(private val context: Context) {
    private val chaveToken = stringPreferencesKey("token")
    private val chaveUsuario = stringPreferencesKey("usuario")
    private val chaveRefresh = stringPreferencesKey("refresh_token")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun salvar(token: String, usuario: UsuarioDto, refreshToken: String? = null) {
        context.dataStore.edit {
            it[chaveToken] = token
            it[chaveUsuario] = json.encodeToString(usuario)
            if (refreshToken != null) it[chaveRefresh] = refreshToken else it.remove(chaveRefresh)
        }
    }

    /** Depois de uma renovação: guarda o access token e o refresh token novos. */
    suspend fun atualizarTokens(token: String, refreshToken: String) {
        context.dataStore.edit {
            it[chaveToken] = token
            it[chaveRefresh] = refreshToken
        }
    }

    suspend fun refreshToken(): String? = context.dataStore.data.first()[chaveRefresh]

    suspend fun carregar(): Pair<String, UsuarioDto>? {
        val prefs = context.dataStore.data.first()
        val token = prefs[chaveToken] ?: return null
        val usuario = prefs[chaveUsuario]?.let { json.decodeFromString<UsuarioDto>(it) } ?: return null
        return token to usuario
    }

    suspend fun limpar() {
        context.dataStore.edit { it.clear() }
    }
}