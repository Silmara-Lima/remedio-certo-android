package br.com.remediocerto.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException

fun mensagemDeErro(e: HttpException): String {
    if (e.code() == 401) return "Sessão expirada. Toque em Sair (aba Mais) e entre novamente."
    val corpo = e.response()?.errorBody()?.string().orEmpty()
    val detalhe = runCatching { Json.parseToJsonElement(corpo).jsonObject["detail"] }.getOrNull()
    return if (detalhe is JsonPrimitive) detalhe.content else "Erro ${e.code()} na API."
}