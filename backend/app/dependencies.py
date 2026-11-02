import time
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.database import get_db
from app.enums import PerfilUsuario
from app.models import Usuario
from app.auth_models import AuthSession
from app.security import decodificar_token


bearer_scheme = HTTPBearer(auto_error=False)


def obter_usuario_atual(
    request: Request,
    creds: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> Usuario:
    if creds is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Envie o token no cabecalho Authorization: Bearer SEU_TOKEN.",
        )

    payload = decodificar_token(creds.credentials)
    if not payload or payload.get("type") != "access":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token invalido ou expirado.")

    id = payload.get("sub")
    if not id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token sem identificador de usuario.")

    try:
        user_id = int(id)
    except (TypeError, ValueError):
        raise HTTPException(401, "Token invalido.")
    session = db.get(AuthSession, payload.get("sid", ""))
    if not session or session.usuario_id != user_id or session.revoked or session.expires_at <= int(time.time()):
        raise HTTPException(401, "Sessao encerrada ou expirada.")
    request.state.auth_session_id = session.id
    user = db.get(Usuario, user_id)
    if not user or not user.ativo:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Usuario nao encontrado ou inativo.")

    return user


def exigir_admin(user: Usuario = Depends(obter_usuario_atual)) -> Usuario:
    if user.perfil != PerfilUsuario.admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Acesso permitido apenas para administrador.",
        )
    return user
