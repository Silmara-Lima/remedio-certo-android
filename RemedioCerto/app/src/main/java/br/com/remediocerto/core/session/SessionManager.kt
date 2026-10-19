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
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun salvar(token: String, usuario: UsuarioDto) {
        context.dataStore.edit {
            it[chaveToken] = token
            it[chaveUsuario] = json.encodeToString(usuario)
        }
    }

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