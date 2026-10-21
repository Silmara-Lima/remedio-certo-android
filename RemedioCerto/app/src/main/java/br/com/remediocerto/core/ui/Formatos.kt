package br.com.remediocerto.core.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

fun dataBr(iso: String): String =
    runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(iso)

fun dataHoraBr(iso: String): String =
    runCatching { LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(iso)

/** "31122027" -> "2027-12-31". Retorna null se a data não existir (ex.: 31/02) ou estiver incompleta. */
fun dataDigitosParaIso(digitos: String): String? = runCatching {
    LocalDate.parse(
        digitos,
        DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT),
    ).toString()
}.getOrNull()