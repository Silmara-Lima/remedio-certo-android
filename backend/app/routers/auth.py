import logging
import secrets
import time
from datetime import datetime

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request
from sqlalchemy import update
from sqlalchemy.orm import Session

from app import email_service
from app.auth_models import AuthSession, PasswordReset, RefreshToken
from app.auth_schemas import RecuperarSenhaIn, RedefinirSenhaIn, RefreshIn, SessionToken
from app.auth_service import bloquear_usuario, emitir_sessao, emitir_tokens, limitar, revogar_sessoes, token_hash, usuario_frontend
from app.config import settings
from app.database import get_db
from app.dependencies import obter_usuario_atual
from app.models import Usuario
from app.schemas import LoginIn, MensagemOut, TrocarSenhaIn, UsuarioAuthOut
from app.security import gerar_hash_senha, verificar_senha

router = APIRouter(prefix="/auth", tags=["Autenticacao"])
logger = logging.getLogger(__name__)
GENERIC_RESET = "Se o e-mail estiver cadastrado, voce recebera as instrucoes de recuperacao."
DUMMY_HASH = gerar_hash_senha(secrets.token_urlsafe(32))


def ip(request):
    return request.client.host if request.client else "unknown"


@router.post("/login", response_model=SessionToken)
def login(dados: LoginIn, request: Request, db: Session = Depends(get_db)):
    limitar(db, f"login:ip:{ip(request)}", 100)
    limitar(db, f"login:email:{str(dados.email).lower()}", 20)
    usuario = bloquear_usuario(db, email=dados.email)
    valid = verificar_senha(dados.senha, usuario.senha_hash if usuario else DUMMY_HASH)
    if not usuario or not usuario.ativo or not valid:
        raise HTTPException(401, "E-mail ou senha invalidos.")
    usuario.ultimo_acesso = datetime.now()
    response = emitir_sessao(db, usuario)
    db.commit()
    return response


@router.post("/refresh", response_model=SessionToken)
def refresh(dados: RefreshIn, request: Request, db: Session = Depends(get_db)):
    limitar(db, f"refresh:ip:{ip(request)}", 120)
    stored = db.get(RefreshToken, token_hash(dados.refresh_token))
    if not stored:
        raise HTTPException(401, "Refresh token invalido ou expirado.")
    session = db.query(AuthSession).filter(AuthSession.id == stored.session_id).with_for_update().first()
    if not session or session.revoked or session.expires_at <= int(time.time()):
        raise HTTPException(401, "Refresh token invalido ou expirado.")
    used = db.execute(update(RefreshToken).where(RefreshToken.token_hash == stored.token_hash, RefreshToken.used.is_(False))
                      .values(used=True)).rowcount
    if not used:
        session.revoked = True
        db.commit()
        raise HTTPException(401, "Refresh token reutilizado. Entre novamente.")
    usuario = db.get(Usuario, session.usuario_id)
    if not usuario or not usuario.ativo:
        session.revoked = True
        db.commit()
        raise HTTPException(401, "Usuario inativo.")
    response = emitir_tokens(db, usuario, session)
    db.commit()
    return response


@router.get("/me", response_model=UsuarioAuthOut)
def me(usuario: Usuario = Depends(obter_usuario_atual)):
    return usuario_frontend(usuario)


@router.post("/logout", response_model=MensagemOut)
def logout(request: Request, db: Session = Depends(get_db), usuario: Usuario = Depends(obter_usuario_atual)):
    db.execute(update(AuthSession).where(AuthSession.id == request.state.auth_session_id).values(revoked=True))
    db.commit()
    return MensagemOut(mensagem="Sessao encerrada com sucesso.")


@router.put("/trocar-senha", response_model=MensagemOut)
def trocar_senha(dados: TrocarSenhaIn, request: Request, usuario: Usuario = Depends(obter_usuario_atual), db: Session = Depends(get_db)):
    usuario = bloquear_usuario(db, user_id=usuario.id)
    current = db.query(AuthSession).filter_by(id=request.state.auth_session_id).populate_existing().first()
    if not current or current.revoked or current.expires_at <= int(time.time()):
        raise HTTPException(401, "Sessao encerrada ou expirada.")
    if not verificar_senha(dados.senha_atual, usuario.senha_hash):
        raise HTTPException(400, "Senha atual incorreta.")
    usuario.senha_hash = gerar_hash_senha(dados.nova_senha)
    revogar_sessoes(db, usuario.id, exceto=request.state.auth_session_id)
    db.commit()
    return MensagemOut(mensagem="Senha alterada com sucesso.")


@router.post("/recuperar-senha", response_model=MensagemOut)
def recuperar_senha(dados: RecuperarSenhaIn, request: Request, background_tasks: BackgroundTasks, db: Session = Depends(get_db)):
    limitar(db, f"reset:ip:{ip(request)}", 20)
    limitar(db, f"reset:email:{str(dados.email).lower()}", 3)
    if not email_service.email_configurado():
        raise HTTPException(503, "Recuperacao por e-mail ainda nao configurada.")
    usuario = bloquear_usuario(db, email=dados.email)
    if usuario and usuario.ativo:
        raw = secrets.token_urlsafe(48)
        db.execute(update(PasswordReset).where(PasswordReset.usuario_id == usuario.id).values(used=True))
        db.add(PasswordReset(token_hash=token_hash(raw), usuario_id=usuario.id,
                             expires_at=int(time.time()) + settings.PASSWORD_RESET_EXPIRE_MINUTES * 60))
        db.commit()
        background_tasks.add_task(_entregar_recuperacao, usuario.email, raw)
    return MensagemOut(mensagem=GENERIC_RESET)


def _entregar_recuperacao(email, raw):
    try:
        email_service.enviar_recuperacao(email, raw)
    except Exception:
        logger.error("Falha na entrega de recuperacao de senha; verificar provedor de e-mail.")


@router.post("/redefinir-senha", response_model=MensagemOut)
def redefinir_senha(dados: RedefinirSenhaIn, request: Request, db: Session = Depends(get_db)):
    limitar(db, f"reset-confirm:ip:{ip(request)}", 30)
    reset = db.get(PasswordReset, token_hash(dados.token))
    if not reset or reset.used or reset.expires_at <= int(time.time()):
        raise HTTPException(400, "Codigo invalido ou expirado.")
    usuario = bloquear_usuario(db, user_id=reset.usuario_id)
    if not usuario or not usuario.ativo:
        raise HTTPException(400, "Codigo invalido ou expirado.")
    claimed = db.execute(update(PasswordReset).where(PasswordReset.token_hash == reset.token_hash, PasswordReset.used.is_(False), PasswordReset.expires_at > int(time.time()))
                         .values(used=True)).rowcount
    if not claimed:
        raise HTTPException(400, "Codigo invalido ou expirado.")
    usuario.senha_hash = gerar_hash_senha(dados.nova_senha)
    revogar_sessoes(db, usuario.id)
    db.commit()
    return MensagemOut(mensagem="Senha redefinida. Entre com a nova senha.")
