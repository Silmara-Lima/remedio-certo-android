from app import email_service
from app.config import settings


def test_https_provider_sends_code_only_to_fixed_provider(monkeypatch):
    calls = []
    monkeypatch.setattr(settings, "RESEND_API_KEY", "test-key")
    monkeypatch.setattr(settings, "EMAIL_FROM", "app@example.com")
    class Response:
        def raise_for_status(self):
            pass
    class Client:
        def __init__(self, **kwargs):
            assert kwargs["timeout"] == 15
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
        def post(self, url, **kwargs):
            calls.append((url, kwargs))
            return Response()
    monkeypatch.setattr(email_service.httpx, "Client", Client)
    assert email_service.email_configurado()
    email_service.enviar_recuperacao("user@example.com", "code-for-test")
    assert calls[0][0] == "https://api.resend.com/emails"
    assert calls[0][1]["json"]["to"] == ["user@example.com"]
    assert "code-for-test" in calls[0][1]["json"]["text"]


def test_smtp_tls_fallback_when_resend_pair_incomplete(monkeypatch):
    events = []
    for field, value in {"RESEND_API_KEY": "partial-key", "EMAIL_FROM": "", "SMTP_HOST": "smtp.example.com",
                         "SMTP_PORT": 587, "SMTP_USERNAME": "app", "SMTP_PASSWORD": "test-only",
                         "SMTP_FROM": "app@example.com", "SMTP_STARTTLS": True}.items():
        monkeypatch.setattr(settings, field, value)
    class SMTP:
        def __init__(self, host, port, timeout):
            assert (host, port, timeout) == ("smtp.example.com", 587, 15)
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
        def ehlo(self):
            events.append("hello")
        def starttls(self, context):
            events.append("tls")
        def login(self, username, password):
            assert "tls" in events
            events.append("login")
        def send_message(self, message):
            assert message["To"] == "user@example.com"
            assert "test-code" in message.get_content()
            events.append("send")
    monkeypatch.setattr(email_service.smtplib, "SMTP", SMTP)
    assert email_service.email_configurado()
    email_service.enviar_recuperacao("user@example.com", "test-code")
    assert events == ["hello", "tls", "hello", "login", "send"]
