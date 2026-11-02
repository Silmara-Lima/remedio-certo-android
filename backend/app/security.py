from datetime import datetime, timedelta, timezone
from uuid import uuid4

from jose import JWTError, jwt
from passlib.context import CryptContext

from app.config import settings


pwd_context = CryptContext(schemes=["bcrypt_sha256", "bcrypt"], deprecated="auto")


def gerar_hash_senha(senha: str) -> str:
    return pwd_context.hash(senha)


def verificar_senha(senha: str, hash: str) -> bool:
    try:
        return pwd_context.verify(senha, hash)
    except (ValueError, TypeError):
        return False


def criar_token_acesso(data: dict) -> str:
    dados = data.copy()
    expira = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    dados.update({"exp": expira, "iat": datetime.now(timezone.utc), "type": "access", "jti": str(uuid4())})
    return jwt.encode(dados, settings.SECRET_KEY, algorithm=settings.ALGORITHM)


def decodificar_token(token: str) -> dict | None:
    try:
        return jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM], options={"require_exp": True, "require_sub": True})
    except JWTError:
        return None
