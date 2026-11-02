from pydantic import BaseModel, EmailStr, Field
from app.schemas import Token


class SessionToken(Token):
    refresh_token: str
    expires_in: int = 900
    refresh_expires_in: int = 604800


class RefreshIn(BaseModel):
    refresh_token: str = Field(min_length=32, max_length=256)


class RecuperarSenhaIn(BaseModel):
    email: EmailStr


class RedefinirSenhaIn(BaseModel):
    token: str = Field(min_length=32, max_length=256)
    nova_senha: str = Field(min_length=6, max_length=120)
