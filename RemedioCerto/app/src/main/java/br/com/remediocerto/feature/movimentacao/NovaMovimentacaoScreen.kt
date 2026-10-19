package br.com.remediocerto.feature.movimentacao

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.feature.scanner.iniciarScanner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun NovaMovimentacaoScreen(usuario: UsuarioDto, abrirScanner: Boolean = false) {
    val vm: MovimentacaoViewModel = viewModel(factory = viewModelFactory {
        initializer { MovimentacaoViewModel(usuario) }
    })
    val context = LocalContext.current
    val escanear = {
        iniciarScanner(context, onCodigo = { vm.processarCodigo(it) }, onErro = { vm.erro = it })
    }

    // Na aba "Escanear", a câmera abre sozinha na primeira vez.
    LaunchedEffect(Unit) {
        if (abrirScanner && !vm.jaAbriuScanner && vm.medicamento == null) {
            vm.jaAbriuScanner = true
            escanear()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nova movimentação", style = MaterialTheme.typography.headlineSmall)

        // Tipo
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TipoMov.entries.forEach { t ->
                FilterChip(selected = vm.tipo == t, onClick = { vm.selecionarTipo(t) }, label = { Text(t.rotulo) })
            }
        }

        // Medicamento
        Titulo("Medicamento")
        val m = vm.medicamento
        if (m == null) {
            OutlinedTextField(
                value = vm.busca, onValueChange = vm::onBuscaChange,
                label = { Text("Buscar por nome ou código") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            vm.sugestoes.take(6).forEach { s ->
                ListItem(
                    headlineContent = { Text(s.nome) },
                    supportingContent = { s.codigoBarras?.let { Text("Código $it") } },
                    modifier = Modifier.clickable { vm.selecionarMedicamento(s) },
                )
            }
            OutlinedButton(onClick = escanear, modifier = Modifier.fillMaxWidth()) {
                Text("Escanear código de barras")
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.nome, style = MaterialTheme.typography.titleMedium)
                        m.codigoBarras?.let { Text("Código $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = vm::trocarMedicamento) { Text("Trocar") }
                }
            }
        }

        // Unidade (só o administrador escolhe; o funcionário usa a própria)
        if (vm.isAdmin) {
            Titulo("Unidade")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.unidades.forEach { u ->
                    FilterChip(selected = vm.unidadeId == u.id, onClick = { vm.selecionarUnidade(u.id) }, label = { Text(u.nome) })
                }
            }
        }

        // Lote
        if (m != null) {
            Titulo("Lote")
            if (vm.tipo == TipoMov.ENTRADA) {
                OutlinedTextField(vm.novoLote, { vm.novoLote = it }, label = { Text("Número do lote") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vm.validade, { vm.validade = it }, label = { Text("Validade (dd/mm/aaaa)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                val lotes = vm.lotesDisponiveis()
                if (lotes.isEmpty()) {
                    Text("Nenhum lote disponível para esta operação nesta unidade.")
                } else {
                    lotes.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.loteSelecionado = l },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = vm.loteSelecionado?.id == l.id, onClick = { vm.loteSelecionado = l })
                            Text("Lote ${l.lote} · ${l.quantidade} un. · val. ${dataBr(l.validade)}")
                        }
                    }
                    if (vm.tipo == TipoMov.SAIDA) {
                        Text("Sugerido o lote que vence primeiro (FEFO).", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Quantidade e dados complementares
        OutlinedTextField(
            vm.quantidade, { vm.quantidade = it.filter(Char::isDigit) },
            label = { Text(if (vm.tipo == TipoMov.AJUSTE) "Saldo correto do lote" else "Quantidade") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(vm.documento, { vm.documento = it }, label = { Text("Nº do documento (opcional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.observacao, { vm.observacao = it },
            label = { Text(if (vm.tipo == TipoMov.AJUSTE) "Justificativa (obrigatória)" else "Observação (opcional)") },
            modifier = Modifier.fillMaxWidth())

        vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        vm.mensagem?.let { Text(it, color = Color(0xFF15803D)) }

        Button(
            onClick = vm::confirmar,
            enabled = !vm.enviando && m != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (vm.enviando) "Registrando..." else "Confirmar ${vm.tipo.rotulo.lowercase()}")
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

private fun dataBr(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(iso)