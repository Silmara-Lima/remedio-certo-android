"""Adiciona a coluna alertas.resolvido_por_id em bancos criados antes dela (RF14).

O init_db cria tabelas que faltam, mas não acrescenta colunas novas em tabelas que já existem.
Pode rodar mais de uma vez: se a coluna já existir, não faz nada.

Uso, dentro de backend/ com o ambiente virtual ativo:
    python -m scripts.migrar_resolvido_por
"""

from sqlalchemy import inspect, text

from app.database import engine


def main() -> None:
    colunas = {coluna["name"] for coluna in inspect(engine).get_columns("alertas")}
    if "resolvido_por_id" in colunas:
        print("Nada a fazer: a coluna resolvido_por_id já existe.")
        return

    with engine.begin() as conexao:
        conexao.execute(text(
            "ALTER TABLE alertas ADD COLUMN resolvido_por_id INTEGER "
            "REFERENCES usuarios(id) ON DELETE SET NULL"
        ))
    print("Coluna resolvido_por_id adicionada à tabela alertas.")


if __name__ == "__main__":
    main()
