"""Idempotently create missing tables; does not alter or delete existing tables."""
from app.database import Base, engine
from app import models, auth_models, push_models, sync_models


def main():
    Base.metadata.create_all(bind=engine)
    print("Tabelas ausentes criadas. Dados existentes preservados.")


if __name__ == "__main__":
    main()
