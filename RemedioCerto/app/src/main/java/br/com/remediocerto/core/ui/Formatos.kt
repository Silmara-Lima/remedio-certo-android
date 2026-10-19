package br.com.remediocerto.core.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun dataBr(iso: String): String =
    runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(iso)

fun dataHoraBr(iso: String): String =
    runCatching { LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(iso)