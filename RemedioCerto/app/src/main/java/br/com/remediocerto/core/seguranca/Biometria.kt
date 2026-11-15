package br.com.remediocerto.core.seguranca

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/** Situação da biometria no aparelho. */
enum class Disponibilidade { DISPONIVEL, SEM_CADASTRO, SEM_SENSOR, ANDROID_ANTIGO }

/** Liga a biometria só quando ela está ativada pelo usuário E o aparelho consegue usá-la. */
fun deveDesbloquearComBiometria(ativada: Boolean, disponibilidade: Disponibilidade): Boolean =
    ativada && disponibilidade == Disponibilidade.DISPONIVEL

/** Explicação mostrada no Perfil quando a biometria não pode ser ligada. */
fun textoDisponibilidade(d: Disponibilidade): String? = when (d) {
    Disponibilidade.DISPONIVEL -> null
    Disponibilidade.SEM_CADASTRO -> "Cadastre uma digital nas configurações do celular para usar esta opção."
    Disponibilidade.SEM_SENSOR -> "Este aparelho não tem leitor biométrico compatível."
    Disponibilidade.ANDROID_ANTIGO -> "O login por biometria precisa do Android 9 ou superior."
}

/**
 * Login por biometria (RF02), com o BiometricPrompt do próprio Android.
 * A biometria não substitui a senha: ela só destrava a sessão já salva no aparelho.
 */
object Biometria {
    private const val PREFS = "seguranca"
    private const val CHAVE_ATIVA = "biometria_ativa"
    private const val CHAVE_OFERECIDA = "biometria_oferecida"

    @Suppress("DEPRECATION")   // canAuthenticate() sem parâmetro é a única opção no Android 10
    fun disponibilidade(context: Context): Disponibilidade {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Disponibilidade.ANDROID_ANTIGO
        val codigo = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                context.getSystemService(BiometricManager::class.java)
                    ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                context.getSystemService(BiometricManager::class.java)?.canAuthenticate()
            else -> {
                // Android 9 não tem como perguntar se há digital cadastrada: confere só se há leitor.
                // Sem digital cadastrada, o próprio Android avisa ao tentar usar.
                return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT))
                    Disponibilidade.DISPONIVEL else Disponibilidade.SEM_SENSOR
            }
        }
        return when (codigo) {
            BiometricManager.BIOMETRIC_SUCCESS -> Disponibilidade.DISPONIVEL
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Disponibilidade.SEM_CADASTRO
            else -> Disponibilidade.SEM_SENSOR
        }
    }

    fun ativada(context: Context): Boolean = prefs(context).getBoolean(CHAVE_ATIVA, false)

    fun definirAtivada(context: Context, ativa: Boolean) {
        prefs(context).edit().putBoolean(CHAVE_ATIVA, ativa).putBoolean(CHAVE_OFERECIDA, true).apply()
    }

    fun deveDesbloquear(context: Context): Boolean =
        deveDesbloquearComBiometria(ativada(context), disponibilidade(context))

    /** Fluxo C do plano: depois do login com senha, oferece a biometria uma vez. */
    fun podeOferecer(context: Context): Boolean =
        !ativada(context) && !prefs(context).getBoolean(CHAVE_OFERECIDA, false) &&
                disponibilidade(context) == Disponibilidade.DISPONIVEL

    fun marcarOferecida(context: Context) {
        prefs(context).edit().putBoolean(CHAVE_OFERECIDA, true).apply()
    }

    /** Ao sair da conta: a próxima pessoa entra com senha e decide se quer biometria. */
    fun limpar(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * Abre a tela de biometria do Android.
     * [onUsarSenha] é chamado quando a pessoa toca em "Usar senha" ou cancela.
     */
    fun autenticar(
        context: Context,
        titulo: String,
        onSucesso: () -> Unit,
        onUsarSenha: () -> Unit,
        onErro: (String) -> Unit = {},
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { onUsarSenha(); return }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(titulo)
            .setSubtitle("Use a biometria cadastrada no celular")
            .setNegativeButton("Usar senha", executor) { _, _ -> onUsarSenha() }
            .build()
        prompt.authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSucesso()

            override fun onAuthenticationError(codigo: Int, mensagem: CharSequence?) {
                if (codigo == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                    codigo == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                ) onUsarSenha()
                else onErro(mensagem?.toString() ?: "Não foi possível usar a biometria.")
            }
            // Tentativa errada (dedo não reconhecido): o próprio Android pede para tentar de novo.
        })
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}