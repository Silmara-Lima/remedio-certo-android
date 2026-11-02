import os
from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

os.environ.setdefault("ALERTS_ENABLED", "false")
os.environ.setdefault("CREATE_TABLES_ON_STARTUP", "false")

from app.database import Base, get_db
from app.enums import PerfilUsuario
from app.main import app
from app.models import ConfiguracaoAlerta, EstoqueLote, Medicamento, UnidadeSaude, Usuario
from app.security import gerar_hash_senha
from app.services import sincronizar_alertas


@pytest.fixture()
def db_session(tmp_path):
    url = os.getenv("TEST_DATABASE_URL", f"sqlite:///{(tmp_path / 'api_test.db').as_posix()}")
    options = {"connect_args": {"check_same_thread": False, "timeout": 30}} if url.startswith("sqlite") else {}
    engine = create_engine(url, **options)
    if url.startswith("sqlite"):
        @event.listens_for(engine, "connect")
        def fk(connection, _):
            connection.execute("PRAGMA foreign_keys=ON")
    TestingSessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)
    Base.metadata.create_all(bind=engine)

    db = TestingSessionLocal()
    seed_test_data(db)
    db.close()

    def override_get_db():
        session = TestingSessionLocal()
        try:
            yield session
        finally:
            session.close()

    app.dependency_overrides[get_db] = override_get_db

    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        app.dependency_overrides.clear()
        Base.metadata.drop_all(bind=engine)
        engine.dispose()


@pytest.fixture()
def client(db_session):
    return TestClient(app)


@pytest.fixture()
def admin_headers(client):
    return auth_headers(client, "admin@remediocerto.com", "123456")


@pytest.fixture()
def funcionario_headers(client):
    return auth_headers(client, "maria@remediocerto.com", "123456")


@pytest.fixture()
def funcionario_outra_unidade_headers(client):
    return auth_headers(client, "joao@remediocerto.com", "123456")


def auth_headers(client: TestClient, email: str, senha: str) -> dict[str, str]:
    response = client.post("/auth/login", json={"email": email, "senha": senha})
    assert response.status_code == 200
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def seed_test_data(db):
    today = date.today()
    test_hash = gerar_hash_senha("123456")

    unidade_centro = UnidadeSaude(
        nome="UBS Centro",
        cnpj="11.111.111/0001-11",
        endereco="Rua Central, 100",
        telefone="(83) 3000-0001",
    )
    unidade_norte = UnidadeSaude(
        nome="Hospital Norte",
        cnpj="22.222.222/0001-22",
        endereco="Av Norte, 200",
        telefone="(83) 3000-0002",
    )
    db.add_all([unidade_centro, unidade_norte])
    db.flush()

    db.add_all(
        [
            ConfiguracaoAlerta(unidade_id=unidade_centro.id, dias_alerta_vencimento=30),
            ConfiguracaoAlerta(unidade_id=unidade_norte.id, dias_alerta_vencimento=30),
        ]
    )

    db.add_all(
        [
            Usuario(
                nome="Administrador",
                email="admin@remediocerto.com",
                senha_hash=test_hash,
                perfil=PerfilUsuario.admin,
                unidade_id=unidade_centro.id,
            ),
            Usuario(
                nome="Maria Silva",
                email="maria@remediocerto.com",
                senha_hash=test_hash,
                perfil=PerfilUsuario.funcionario,
                unidade_id=unidade_centro.id,
            ),
            Usuario(
                nome="Joao Santos",
                email="joao@remediocerto.com",
                senha_hash=test_hash,
                perfil=PerfilUsuario.funcionario,
                unidade_id=unidade_norte.id,
            ),
        ]
    )

    paracetamol = Medicamento(
        nome="Paracetamol 500mg",
        principio_ativo="Paracetamol",
        fabricante="EMS",
        forma_farmaceutica="Comprimido",
        dosagem="500mg",
        codigo_barras="7891000000011",
        registro_anvisa="TESTE-0001",
        quantidade_minima=50,
    )
    dipirona = Medicamento(
        nome="Dipirona 1g",
        principio_ativo="Dipirona",
        fabricante="Medley",
        forma_farmaceutica="Comprimido",
        dosagem="1g",
        codigo_barras="7891000000028",
        registro_anvisa="TESTE-0002",
        quantidade_minima=30,
    )
    amoxicilina = Medicamento(
        nome="Amoxicilina 500mg",
        principio_ativo="Amoxicilina",
        fabricante="Eurofarma",
        forma_farmaceutica="Capsula",
        dosagem="500mg",
        codigo_barras="7891000000035",
        registro_anvisa="TESTE-0003",
        quantidade_minima=40,
    )
    db.add_all([paracetamol, dipirona, amoxicilina])
    db.flush()

    db.add_all(
        [
            EstoqueLote(
                medicamento_id=paracetamol.id,
                unidade_id=unidade_centro.id,
                lote="LOTE-NORMAL",
                data_fabricacao=today - timedelta(days=60),
                validade=today + timedelta(days=180),
                quantidade_inicial=100,
                quantidade=100,
            ),
            EstoqueLote(
                medicamento_id=dipirona.id,
                unidade_id=unidade_centro.id,
                lote="LOTE-BAIXO",
                data_fabricacao=today - timedelta(days=60),
                validade=today + timedelta(days=10),
                quantidade_inicial=20,
                quantidade=20,
            ),
            EstoqueLote(
                medicamento_id=amoxicilina.id,
                unidade_id=unidade_norte.id,
                lote="LOTE-VENCIDO",
                data_fabricacao=today - timedelta(days=300),
                validade=today - timedelta(days=1),
                quantidade_inicial=10,
                quantidade=10,
            ),
        ]
    )
    db.flush()
    sincronizar_alertas(db)
    db.commit()


@pytest.fixture(autouse=True)
def fast_test_passwords(monkeypatch):
    from passlib.context import CryptContext
    import app.security as security
    monkeypatch.setattr(security, "pwd_context", CryptContext(schemes=["bcrypt_sha256", "bcrypt"], bcrypt_sha256__rounds=4, bcrypt__rounds=4))
