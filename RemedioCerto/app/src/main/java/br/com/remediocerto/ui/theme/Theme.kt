package br.com.remediocerto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
    primary = Primaria,
    onPrimary = Color.White,
    primaryContainer = PrimariaClara,
    onPrimaryContainer = PrimariaEscura,
    secondary = Secundaria,
    onSecondary = Color.White,
    secondaryContainer = PrimariaClara,          // chips de filtro selecionados
    onSecondaryContainer = PrimariaEscura,
    error = Critico,
    onError = Color.White,
    background = FundoClaro,
    onBackground = TextoPrincipal,
    surface = FundoClaro,
    onSurface = TextoPrincipal,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = TextoSecundario,
    surfaceContainer = Color.White,              // barra de navegação inferior
    surfaceContainerHighest = Color(0xFFE6F4F1), // cartões
    outline = Color(0xFFCBD5E1),
)

private val EsquemaEscuro = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = PrimariaClara,
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF172554),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = PrimariaClara,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    background = FundoEscuro,
    onBackground = Color(0xFFE2E8F0),
    surface = FundoEscuro,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF111C33),
    surfaceContainerHighest = Color(0xFF1E293B),
    outline = Color(0xFF334155),
)

@Composable
fun RemedioCertoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EsquemaEscuro else EsquemaClaro,
        typography = Typography,
        content = content,
    )
}