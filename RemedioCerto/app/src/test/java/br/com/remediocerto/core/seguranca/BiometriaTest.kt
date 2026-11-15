package br.com.remediocerto.core.seguranca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometriaTest {

    @Test
    fun `so pede biometria quando ativada e disponivel`() {
        assertTrue(deveDesbloquearComBiometria(true, Disponibilidade.DISPONIVEL))
        assertFalse(deveDesbloquearComBiometria(false, Disponibilidade.DISPONIVEL))
    }

    @Test
    fun `digital apagada do celular volta para a senha`() {
        // Ativada no app, mas a pessoa removeu a digital do aparelho: não pode travar o acesso.
        assertFalse(deveDesbloquearComBiometria(true, Disponibilidade.SEM_CADASTRO))
        assertFalse(deveDesbloquearComBiometria(true, Disponibilidade.SEM_SENSOR))
    }

    @Test
    fun `explicacao para cada situacao`() {
        assertNull(textoDisponibilidade(Disponibilidade.DISPONIVEL))
        assertNotNull(textoDisponibilidade(Disponibilidade.SEM_CADASTRO))
        assertNotNull(textoDisponibilidade(Disponibilidade.SEM_SENSOR))
        assertTrue(textoDisponibilidade(Disponibilidade.ANDROID_ANTIGO)!!.contains("Android 9"))
    }
}
