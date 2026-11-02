"""Explicit bootstrap: never creates a default password or runs on deployment."""
import getpass
import os
from pydantic import TypeAdapter, EmailStr
from app.database import SessionLocal
from app.enums import PerfilUsuario
from app.models import Usuario
from app.security import gerar_hash_senha


def main():
    email = TypeAdapter(EmailStr).validate_python(os.getenv("BOOTSTRAP_ADMIN_EMAIL") or input("E-mail: "))
    nome = os.getenv("BOOTSTRAP_ADMIN_NOME") or input("Nome: ")
    senha = os.getenv("BOOTSTRAP_ADMIN_PASSWORD") or getpass.getpass("Senha (mínimo 12 caracteres): ")
    if len(senha) < 12 or len(senha) > 120 or len(nome.strip()) < 2:
        raise SystemExit("Informe nome e senha válidos (12 a 120 caracteres).")
    with SessionLocal() as db:
        if db.query(Usuario).filter(Usuario.email == email).first():
            raise SystemExit("Usuário já existe; nenhuma alteração realizada.")
        db.add(Usuario(nome=nome.strip(), email=email, senha_hash=gerar_hash_senha(senha), perfil=PerfilUsuario.admin))
        db.commit()
    print("Administrador criado. Senha não exibida.")


if __name__ == "__main__":
    main()
