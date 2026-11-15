"""RF03: recuperação de senha com código de 6 números enviado por e-mail."""

import pytest

from app.routers import auth as rota_auth

EMAIL = "maria@remediocerto.com"


@pytest.fixture
def emails(monkeypatch):
    """Simula o provedor de e-mail e guarda os códigos enviados."""
    enviados = []
    monkeypatch.setattr(rota_auth.email_service, "email_configurado", lambda: True)
    monkeypatch.setattr(
        rota_auth.email_service, "enviar_recuperacao", lambda email, codigo: enviados.append((email, codigo))
    )
    return enviados


def _pedir_codigo(client, emails):
    resposta = client.post("/auth/recuperar-senha", json={"email": EMAIL})
    assert resposta.status_code == 200, resposta.text
    return emails[-1][1]


def _redefinir(client, codigo, senha="NovaSenha123"):
    return client.post("/auth/redefinir-senha", json={"email": EMAIL, "codigo": codigo, "nova_senha": senha})


def test_codigo_tem_6_numeros_e_redefine_a_senha(client, emails):
    codigo = _pedir_codigo(client, emails)
    assert len(codigo) == 6 and codigo.isdigit()

    assert _redefinir(client, codigo).status_code == 200
    login = client.post("/auth/login", json={"email": EMAIL, "senha": "NovaSenha123"})
    assert login.status_code == 200, login.text


def test_codigo_errado_e_recusado(client, emails):
    codigo = _pedir_codigo(client, emails)
    errado = f"{(int(codigo) + 1) % 1_000_000:06d}"
    assert _redefinir(client, errado).status_code == 400


def test_codigo_vale_uma_vez_so(client, emails):
    codigo = _pedir_codigo(client, emails)
    assert _redefinir(client, codigo).status_code == 200
    assert _redefinir(client, codigo, senha="OutraSenha456").status_code == 400


def test_novo_pedido_invalida_o_codigo_anterior(client, emails):
    primeiro = _pedir_codigo(client, emails)
    segundo = _pedir_codigo(client, emails)
    if primeiro != segundo:
        assert _redefinir(client, primeiro).status_code == 400
    assert _redefinir(client, segundo).status_code == 200


def test_tentativas_limitadas_por_email(client, emails):
    codigo = _pedir_codigo(client, emails)
    errado = f"{(int(codigo) + 1) % 1_000_000:06d}"
    for _ in range(5):
        assert _redefinir(client, errado).status_code == 400
    assert _redefinir(client, codigo).status_code == 429


def test_codigo_fora_do_formato_e_rejeitado(client, emails):
    _pedir_codigo(client, emails)
    assert _redefinir(client, "12ab56").status_code == 422
