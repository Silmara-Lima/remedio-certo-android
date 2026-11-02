import smtplib
import ssl
from email.message import EmailMessage

import httpx
from app.config import settings


def email_configurado():
    return bool((settings.RESEND_API_KEY and settings.EMAIL_FROM) or (settings.SMTP_HOST and settings.SMTP_FROM))


def enviar_recuperacao(email: str, token: str):
    text = ("Você solicitou a recuperação de senha do Remédio Certo.\n\n"
            f"Código de recuperação: {token}\n\n"
            f"Válido por {settings.PASSWORD_RESET_EXPIRE_MINUTES} minutos e para um único uso. "
            "Se não solicitou, ignore esta mensagem.")
    if settings.RESEND_API_KEY and settings.EMAIL_FROM:
        # Render Free blocks SMTP; HTTPS is also supported.
        with httpx.Client(timeout=15) as client:
            response = client.post("https://api.resend.com/emails", headers={"Authorization": f"Bearer {settings.RESEND_API_KEY}"},
                                   json={"from": settings.EMAIL_FROM, "to": [email], "subject": "Remédio Certo - recuperar senha", "text": text})
            response.raise_for_status()
        return
    message = EmailMessage()
    message["From"] = settings.SMTP_FROM
    message["To"] = email
    message["Subject"] = "Remédio Certo - recuperar senha"
    message.set_content(text)
    with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT, timeout=15) as smtp:
        smtp.ehlo()
        if settings.SMTP_STARTTLS:
            smtp.starttls(context=ssl.create_default_context())
            smtp.ehlo()
        if settings.SMTP_USERNAME:
            smtp.login(settings.SMTP_USERNAME, settings.SMTP_PASSWORD)
        smtp.send_message(message)
