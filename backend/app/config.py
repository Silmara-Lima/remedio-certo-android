from functools import lru_cache
import secrets

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    APP_NAME: str = "Remedio Certo API"
    APP_VERSION: str = "2.0.0"

    DATABASE_URL: str = "sqlite:///./remedio_certo_dev.db"

    ENVIRONMENT: str = "development"
    SECRET_KEY: str = ""
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    PASSWORD_RESET_EXPIRE_MINUTES: int = 15
    SMTP_HOST: str = ""
    SMTP_PORT: int = 587
    SMTP_USERNAME: str = ""
    SMTP_PASSWORD: str = ""
    SMTP_FROM: str = ""
    SMTP_STARTTLS: bool = True
    RESEND_API_KEY: str = ""
    EMAIL_FROM: str = ""
    FIREBASE_CREDENTIALS_JSON: str = ""
    ALERTS_ENABLED: bool = True
    ALERTS_INTERVAL_SECONDS: int = 60
    TIMEZONE: str = "America/Sao_Paulo"

    CREATE_TABLES_ON_STARTUP: bool = True

    ALLOW_ORIGINS: str = "*"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    @model_validator(mode="after")
    def validar_ambiente(self):
        if self.ALGORITHM != "HS256":
            raise ValueError("Esta API utiliza HS256.")
        if self.ENVIRONMENT == "production":
            if len(self.SECRET_KEY) < 32 or "troque" in self.SECRET_KEY.lower():
                raise ValueError("Defina SECRET_KEY aleatória com ao menos 32 caracteres.")
            if self.DATABASE_URL.startswith("sqlite"):
                raise ValueError("Use PostgreSQL ou MySQL persistente em produção.")
            if self.SMTP_HOST and not self.SMTP_STARTTLS:
                raise ValueError("SMTP em produção exige TLS.")
        elif not self.SECRET_KEY:
            self.SECRET_KEY = secrets.token_urlsafe(48)
        if min(self.ACCESS_TOKEN_EXPIRE_MINUTES, self.REFRESH_TOKEN_EXPIRE_DAYS, self.PASSWORD_RESET_EXPIRE_MINUTES, self.ALERTS_INTERVAL_SECONDS) <= 0:
            raise ValueError("Durações devem ser positivas.")
        return self

    @property
    def allow_origins_list(self) -> list[str]:
        if self.ALLOW_ORIGINS.strip() == "*":
            return ["*"]
        return [x.strip() for x in self.ALLOW_ORIGINS.split(",") if x.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
