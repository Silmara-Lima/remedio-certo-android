package br.com.remediocerto

import android.app.Application
import br.com.remediocerto.core.session.SessionManager

class RemedioCertoApp : Application() {
    lateinit var sessao: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        sessao = SessionManager(this)
    }
}