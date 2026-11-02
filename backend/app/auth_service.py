import hashlib
import hmac
import secrets
import time
from uuid import uuid4

from fastapi import HTTPException
from sqlalchemy import delete, update
from sqlalchemy.exc import IntegrityError

from app.auth_models import AuthRateLimit, AuthSession, PasswordReset, RefreshToken
from app.config import settings
from app.security import criar_token_acesso
from app.models import Usuario


def bloquear_usuario(db, user_id=None, email=None):
    """Serialize password verification/session creation with password changes."""
    if db.get_bind().dialect.name == "sqlite":
        connection = db.connection()
        if not connection.connection.driver_connection.in_transaction:
            connection.exec_driver_sql("BEGIN IMMEDIATE")
    query = db.query(Usuario)
    query = query.filter(Usuario.id == user_id) if user_id is not None else query.filter(Usuario.email == email)
    return query.populate_existing().with_for_update().first()


def token_hash(token: str):
    return hashlib.sha256(token.encode()).hexdigest()


def limitar(db, identity: str, maximum: int, seconds: int = 900):
    now = int(time.time())
    window = now // seconds * seconds
    key = hmac.new(settings.SECRET_KEY.encode(), f"{window}:{identity}".encode(), hashlib.sha256).hexdigest()
    if db.get(AuthRateLimit, key) is None:
        try:
            with db.begin_nested():
                db.add(AuthRateLimit(key=key, window_start=window, count=0))
                db.flush()
        except IntegrityError:
            pass
    changed = db.execute(update(AuthRateLimit).where(AuthRateLimit.key == key, AuthRateLimit.count < maximum)
                         .values(count=AuthRateLimit.count + 1)).rowcount
    db.execute(delete(AuthRateLimit).where(AuthRateLimit.window_start < now - 86400))
    db.commit()
    if not changed:
        raise HTTPException(429, "Muitas tentativas. Tente novamente mais tarde.", headers={"Retry-After": str(seconds)})


def emitir_sessao(db, usuario):
    session = AuthSession(id=str(uuid4()), usuario_id=usuario.id,
                          expires_at=int(time.time()) + settings.REFRESH_TOKEN_EXPIRE_DAYS * 86400)
    db.add(session)
    db.flush()
    return emitir_tokens(db, usuario, session)


def emitir_tokens(db, usuario, session):
    refresh = secrets.token_urlsafe(48)
    db.add(RefreshToken(token_hash=token_hash(refresh), session_id=session.id))
    return {"access_token": criar_token_acesso({"sub": str(usuario.id), "sid": session.id}),
            "token_type": "bearer", "refresh_token": refresh,
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "refresh_expires_in": max(0, session.expires_at - int(time.time())),
            "usuario": usuario_frontend(usuario)}


def usuario_frontend(usuario):
    return {"id": usuario.id, "nome": usuario.nome, "email": usuario.email,
            "tipo": usuario.perfil.value.upper(), "ativo": usuario.ativo, "unidadeSaudeId": usuario.unidade_id}


def revogar_sessoes(db, usuario_id, exceto: str | None = None):
    query = update(AuthSession).where(AuthSession.usuario_id == usuario_id)
    if exceto:
        query = query.where(AuthSession.id != exceto)
    db.execute(query.values(revoked=True))
    db.execute(update(PasswordReset).where(PasswordReset.usuario_id == usuario_id).values(used=True))
