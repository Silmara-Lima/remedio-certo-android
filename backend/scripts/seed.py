from datetime import date
from pathlib import Path
import sys


ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.append(str(ROOT_DIR))

from app.database import Base, SessionLocal, engine
from app.enums import PerfilUsuario, TipoMovimentacao
from app.models import ConfiguracaoAlerta, EstoqueLote, Medicamento, Movimentacao, UnidadeSaude, Usuario
from app.security import gerar_hash_senha
from app.services import sincronizar_alertas
from app.config import settings


def criar_dados_iniciais():
    if settings.ENVIRONMENT == "production":
        raise SystemExit("Seed demonstrativo desabilitado em produção. Use python -m app.create_admin.")
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()

    try:
        if db.query(UnidadeSaude).count() > 0:
            print("O banco ja possui dados. Nada foi criado.")
            return

        unidades = [
            UnidadeSaude(nome="UBS Centro", cnpj="12.345.678/0001-90", endereco="Rua das Flores, 123 - Centro", telefone="(83) 3333-1111"),
            UnidadeSaude(nome="Hospital Sao Lucas", cnpj="34.567.890/0001-34", endereco="Rua Sao Paulo, 789 - Jardim America", telefone="(83) 3333-2222"),
            UnidadeSaude(nome="UPA 24h Vila Nova", cnpj="23.456.789/0001-12", endereco="Av. Brasil, 456 - Vila Nova", telefone="(83) 3333-3333"),
        ]
        db.add_all(unidades)
        db.flush()

        configuracoes = [
            ConfiguracaoAlerta(unidade_id=unidades[0].id, estoque_minimo_padrao=10, dias_alerta_vencimento=30, dias_alerta_critico=7),
            ConfiguracaoAlerta(unidade_id=unidades[1].id, estoque_minimo_padrao=10, dias_alerta_vencimento=30, dias_alerta_critico=7),
            ConfiguracaoAlerta(unidade_id=unidades[2].id, estoque_minimo_padrao=10, dias_alerta_vencimento=30, dias_alerta_critico=7),
        ]
        db.add_all(configuracoes)

        usuarios = [
            Usuario(nome="Administrador", email="admin@remediocerto.com", senha_hash=gerar_hash_senha("123456"), perfil=PerfilUsuario.admin, unidade_id=unidades[0].id),
            Usuario(nome="Maria Silva", email="maria.silva@remediocerto.com", senha_hash=gerar_hash_senha("123456"), perfil=PerfilUsuario.funcionario, unidade_id=unidades[0].id),
            Usuario(nome="Joao Santos", email="joao.santos@remediocerto.com", senha_hash=gerar_hash_senha("123456"), perfil=PerfilUsuario.funcionario, unidade_id=unidades[1].id),
        ]
        db.add_all(usuarios)
        db.flush()

        medicamentos = [
            Medicamento(nome="Paracetamol 500mg", principio_ativo="Paracetamol", fabricante="EMS", forma_farmaceutica="Comprimido", dosagem="500mg", codigo_barras="7890000000011", registro_anvisa="ANVISA-000001", quantidade_minima=50),
            Medicamento(nome="Dipirona 1g", principio_ativo="Dipirona Sodica", fabricante="Medley", forma_farmaceutica="Comprimido", dosagem="1g", codigo_barras="7890000000028", registro_anvisa="ANVISA-000002", quantidade_minima=30),
            Medicamento(nome="Amoxicilina 500mg", principio_ativo="Amoxicilina", fabricante="Eurofarma", forma_farmaceutica="Capsula", dosagem="500mg", codigo_barras="7890000000035", registro_anvisa="ANVISA-000003", quantidade_minima=40),
            Medicamento(nome="Omeprazol 20mg", principio_ativo="Omeprazol", fabricante="Ache", forma_farmaceutica="Capsula", dosagem="20mg", codigo_barras="7890000000042", registro_anvisa="ANVISA-000004", quantidade_minima=25),
            Medicamento(nome="Losartana 50mg", principio_ativo="Losartana Potassica", fabricante="Sandoz", forma_farmaceutica="Comprimido", dosagem="50mg", codigo_barras="7890000000059", registro_anvisa="ANVISA-000005", quantidade_minima=60),
        ]
        db.add_all(medicamentos)
        db.flush()

        estoques = [
            EstoqueLote(medicamento_id=medicamentos[0].id, unidade_id=unidades[0].id, lote="L2024A", data_fabricacao=date(2024, 1, 10), validade=date(2026, 12, 15), quantidade_inicial=500, quantidade=450),
            EstoqueLote(medicamento_id=medicamentos[1].id, unidade_id=unidades[0].id, lote="L2024B", data_fabricacao=date(2024, 2, 5), validade=date(2026, 6, 10), quantidade_inicial=200, quantidade=23),
            EstoqueLote(medicamento_id=medicamentos[2].id, unidade_id=unidades[1].id, lote="L2024C", data_fabricacao=date(2024, 3, 1), validade=date(2026, 6, 5), quantidade_inicial=300, quantidade=12),
            EstoqueLote(medicamento_id=medicamentos[3].id, unidade_id=unidades[1].id, lote="L2024D", data_fabricacao=date(2024, 1, 20), validade=date(2026, 9, 30), quantidade_inicial=150, quantidade=80),
            EstoqueLote(medicamento_id=medicamentos[4].id, unidade_id=unidades[2].id, lote="L2024E", data_fabricacao=date(2024, 4, 15), validade=date(2027, 3, 20), quantidade_inicial=400, quantidade=320),
        ]
        db.add_all(estoques)
        db.flush()

        movimentacoes = [
            Movimentacao(tipo=TipoMovimentacao.entrada, medicamento_id=medicamentos[0].id, unidade_id=unidades[0].id, estoque_lote_id=estoques[0].id, usuario_id=usuarios[1].id, lote="L2024A", validade=date(2026, 12, 15), quantidade=500, numero_documento="NF-001", observacao="Compra mensal programada"),
            Movimentacao(tipo=TipoMovimentacao.saida, medicamento_id=medicamentos[1].id, unidade_id=unidades[0].id, estoque_lote_id=estoques[1].id, usuario_id=usuarios[1].id, lote="L2024B", validade=date(2026, 6, 10), quantidade=50, numero_documento="REQ-001", observacao="Dispensacao para pacientes"),
            Movimentacao(tipo=TipoMovimentacao.entrada, medicamento_id=medicamentos[2].id, unidade_id=unidades[1].id, estoque_lote_id=estoques[2].id, usuario_id=usuarios[2].id, lote="L2024C", validade=date(2026, 6, 5), quantidade=300, numero_documento="NF-002", observacao="Doacao da Secretaria de Saude"),
            Movimentacao(tipo=TipoMovimentacao.saida, medicamento_id=medicamentos[3].id, unidade_id=unidades[1].id, estoque_lote_id=estoques[3].id, usuario_id=usuarios[2].id, lote="L2024D", validade=date(2026, 9, 30), quantidade=70, numero_documento="REQ-002", observacao="Uso rotineiro na internacao"),
        ]
        db.add_all(movimentacoes)

        sincronizar_alertas(db)
        db.commit()
        print("Dados iniciais criados com sucesso.")
    finally:
        db.close()


if __name__ == "__main__":
    criar_dados_iniciais()
