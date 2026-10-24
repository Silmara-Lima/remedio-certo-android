from datetime import date, timedelta


def assert_json_response(response, expected_status=200):
    assert response.status_code == expected_status, response.text
    return response.json()


def test_health_root_and_authentication_flow(client, admin_headers):
    assert_json_response(client.get("/health")) == {"status": "ok"}
    root = assert_json_response(client.get("/"))
    assert root["documentacao"] == "/docs"

    assert client.get("/auth/me").status_code == 401
    assert client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "errada"}).status_code == 401

    me = assert_json_response(client.get("/auth/me", headers=admin_headers))
    assert me["email"] == "admin@remediocerto.com"
    assert me["tipo"] == "ADMIN"

    changed = assert_json_response(
        client.put(
            "/auth/trocar-senha",
            headers=admin_headers,
            json={"senha_atual": "123456", "nova_senha": "nova123"},
        )
    )
    assert changed["mensagem"] == "Senha alterada com sucesso."
    assert client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "123456"}).status_code == 401
    assert client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "nova123"}).status_code == 200


def test_admin_permissions_and_unidades_crud(client, admin_headers, funcionario_headers):
    assert client.post("/unidades", headers=funcionario_headers, json={"nome": "Posto Sem Permissao"}).status_code == 403

    created = assert_json_response(
        client.post(
            "/unidades",
            headers=admin_headers,
            json={
                "nome": "UPA Sul",
                "cnpj": "33.333.333/0001-33",
                "endereco": "Rua Sul, 300",
                "telefone": "(83) 3000-0003",
            },
        )
    )
    unidade_id = created["id"]

    assert assert_json_response(client.get(f"/unidades/{unidade_id}", headers=admin_headers))["nome"] == "UPA Sul"
    assert len(assert_json_response(client.get("/unidades?busca=UPA", headers=admin_headers))) == 1

    updated = assert_json_response(
        client.put(
            f"/unidades/{unidade_id}",
            headers=admin_headers,
            json={"telefone": "(83) 3333-3333"},
        )
    )
    assert updated["telefone"] == "(83) 3333-3333"

    assert client.post("/unidades", headers=admin_headers, json={"nome": "UPA Sul"}).status_code == 400
    deleted = assert_json_response(client.delete(f"/unidades/{unidade_id}", headers=admin_headers))
    assert "desativada" in deleted["mensagem"]
    assert all(item["id"] != unidade_id for item in assert_json_response(client.get("/unidades", headers=admin_headers)))


def test_usuarios_crud_and_validation(client, admin_headers, funcionario_headers):
    assert client.get("/usuarios", headers=funcionario_headers).status_code == 403
    assert (
        client.post(
            "/usuarios",
            headers=admin_headers,
            json={
                "nome": "Funcionario Sem Unidade",
                "email": "sem.unidade@remediocerto.com",
                "senha": "123456",
                "perfil": "funcionario",
            },
        ).status_code
        == 400
    )

    unidade_id = assert_json_response(client.get("/unidades", headers=admin_headers))[0]["id"]
    created = assert_json_response(
        client.post(
            "/usuarios",
            headers=admin_headers,
            json={
                "nome": "Carlos Teste",
                "email": "carlos@remediocerto.com",
                "senha": "123456",
                "perfil": "funcionario",
                "unidade_id": unidade_id,
            },
        )
    )
    usuario_id = created["id"]

    listed = assert_json_response(client.get("/usuarios?busca=carlos", headers=admin_headers))
    assert [user["email"] for user in listed] == ["carlos@remediocerto.com"]

    updated = assert_json_response(
        client.put(
            f"/usuarios/{usuario_id}",
            headers=admin_headers,
            json={"nome": "Carlos Atualizado", "senha": "abcdef"},
        )
    )
    assert updated["nome"] == "Carlos Atualizado"
    assert client.post("/auth/login", json={"email": "carlos@remediocerto.com", "senha": "abcdef"}).status_code == 200

    current_admin_id = assert_json_response(client.get("/auth/me", headers=admin_headers))["id"]
    assert client.delete(f"/usuarios/{current_admin_id}", headers=admin_headers).status_code == 400
    assert_json_response(client.delete(f"/usuarios/{usuario_id}", headers=admin_headers))
    assert assert_json_response(client.get(f"/usuarios/{usuario_id}", headers=admin_headers))["ativo"] is False


def test_medicamentos_crud_search_and_soft_delete(client, admin_headers, funcionario_headers):
    assert client.post("/medicamentos", headers=funcionario_headers, json={"nome": "Sem Permissao"}).status_code == 403

    created = assert_json_response(
        client.post(
            "/medicamentos",
            headers=admin_headers,
            json={
                "nome": "Ibuprofeno 600mg",
                "principio_ativo": "Ibuprofeno",
                "fabricante": "Teste Farma",
                "forma_farmaceutica": "Comprimido",
                "dosagem": "600mg",
                "codigo_barras": "7899990000011",
                "registro_anvisa": "TESTE-IBU",
                "quantidade_minima": 15,
            },
        )
    )
    medicamento_id = created["id"]

    assert assert_json_response(client.get(f"/medicamentos/{medicamento_id}", headers=admin_headers))["nome"] == "Ibuprofeno 600mg"
    assert len(assert_json_response(client.get("/medicamentos?busca=ibuprofeno", headers=admin_headers))) == 1

    updated = assert_json_response(
        client.put(
            f"/medicamentos/{medicamento_id}",
            headers=admin_headers,
            json={"quantidade_minima": 25},
        )
    )
    assert updated["quantidade_minima"] == 25

    assert client.post("/medicamentos", headers=admin_headers, json={"nome": "Outro", "codigo_barras": "7899990000011"}).status_code == 400
    assert_json_response(client.delete(f"/medicamentos/{medicamento_id}", headers=admin_headers))
    assert all(item["id"] != medicamento_id for item in assert_json_response(client.get("/medicamentos", headers=admin_headers)))


def test_estoque_filters_crud_and_unit_access(client, admin_headers, funcionario_headers, funcionario_outra_unidade_headers):
    unidade_id = assert_json_response(client.get("/unidades?busca=UBS", headers=admin_headers))[0]["id"]
    medicamento_id = assert_json_response(client.get("/medicamentos?busca=Paracetamol", headers=admin_headers))[0]["id"]

    estoque_baixo = assert_json_response(client.get("/estoque?estoque_baixo=true", headers=admin_headers))
    assert any(item["status_estoque"] in {"estoque_baixo", "vencimento_proximo"} for item in estoque_baixo)
    assert len(assert_json_response(client.get(f"/estoque?unidade={unidade_id}", headers=admin_headers))) >= 1

    created = assert_json_response(
        client.post(
            "/estoque",
            headers=admin_headers,
            json={
                "medicamento_id": medicamento_id,
                "unidade_id": unidade_id,
                "lote": "LOTE-CRUD",
                "data_fabricacao": str(date.today() - timedelta(days=10)),
                "validade": str(date.today() + timedelta(days=90)),
                "quantidade_inicial": 40,
                "quantidade": 40,
            },
        )
    )
    estoque_id = created["id"]

    assert client.get(f"/estoque/{estoque_id}", headers=funcionario_headers).status_code == 200
    other_unit_stock_id = assert_json_response(client.get("/estoque?vencidos=true", headers=admin_headers))[0]["id"]
    assert client.get(f"/estoque/{other_unit_stock_id}", headers=funcionario_headers).status_code == 403
    assert client.get(f"/estoque/{estoque_id}", headers=funcionario_outra_unidade_headers).status_code == 403

    updated = assert_json_response(
        client.put(f"/estoque/{estoque_id}", headers=admin_headers, json={"quantidade": 5})
    )
    assert updated["quantidade"] == 5
    assert updated["status_estoque"] == "estoque_baixo"

    assert client.post("/estoque", headers=funcionario_headers, json=created).status_code == 403
    assert_json_response(client.delete(f"/estoque/{estoque_id}", headers=admin_headers))
    assert client.get(f"/estoque/{estoque_id}", headers=admin_headers).status_code == 404


def test_movimentacoes_update_stock_filters_and_errors(client, admin_headers, funcionario_headers, funcionario_outra_unidade_headers):
    unidade_id = assert_json_response(client.get("/unidades?busca=UBS", headers=admin_headers))[0]["id"]
    medicamento_id = assert_json_response(client.get("/medicamentos?busca=Paracetamol", headers=admin_headers))[0]["id"]
    validade = str(date.today() + timedelta(days=365))

    entrada = assert_json_response(
        client.post(
            "/movimentacoes",
            headers=funcionario_headers,
            json={
                "tipo": "entrada",
                "medicamento_id": medicamento_id,
                "unidade_id": unidade_id,
                "lote": "MOV-001",
                "validade": validade,
                "quantidade": 25,
                "numero_documento": "NF-TESTE",
            },
        )
    )
    assert entrada["usuario"]["email"] == "maria@remediocerto.com"

    assert (
        client.post(
            "/movimentacoes",
            headers=funcionario_outra_unidade_headers,
            json={
                "tipo": "entrada",
                "medicamento_id": medicamento_id,
                "unidade_id": unidade_id,
                "lote": "MOV-NEGADO",
                "validade": validade,
                "quantidade": 1,
            },
        ).status_code
        == 403
    )

    saida = assert_json_response(
        client.post(
            "/movimentacoes",
            headers=funcionario_headers,
            json={
                "tipo": "saida",
                "medicamento_id": medicamento_id,
                "unidade_id": unidade_id,
                "lote": "MOV-001",
                "validade": validade,
                "quantidade": 10,
            },
        )
    )
    assert saida["quantidade"] == 10

    filtered = assert_json_response(client.get(f"/movimentacoes?unidadeId={unidade_id}&tipo=saida", headers=admin_headers))
    assert any(item["id"] == saida["id"] for item in filtered)
    assert assert_json_response(client.get(f"/movimentacoes/{saida['id']}", headers=funcionario_headers))["id"] == saida["id"]

    assert (
        client.post(
            "/movimentacoes",
            headers=funcionario_headers,
            json={
                "tipo": "saida",
                "medicamento_id": medicamento_id,
                "unidade_id": unidade_id,
                "lote": "MOV-001",
                "validade": validade,
                "quantidade": 999,
            },
        ).status_code
        == 400
    )
    assert client.get("/movimentacoes?tipo=invalido", headers=admin_headers).status_code == 400


def test_alertas_generation_frontend_contract_and_status_transitions(client, admin_headers, funcionario_headers):
    assert_json_response(client.post("/alertas/gerar", headers=admin_headers))

    baixo = assert_json_response(client.get("/alertas/estoque-baixo", headers=admin_headers))
    assert baixo
    assert baixo[0]["tipo"] == "ESTOQUE_BAIXO"
    assert baixo[0]["status"] == "PENDENTE"
    assert "dataGeracao" in baixo[0]

    validade = assert_json_response(client.get("/alertas/validade", headers=admin_headers))
    assert validade
    assert all(item["tipo"] == "VALIDADE" for item in validade)

    alerta_id = baixo[0]["id"]
    assert_json_response(client.get(f"/alertas/{alerta_id}", headers=funcionario_headers))
    resolved = assert_json_response(client.patch(f"/alertas/{alerta_id}/resolver", headers=funcionario_headers))
    assert resolved["status"] == "resolvido"
    assert resolved["resolvido_em"] is not None

    ignored = assert_json_response(
        client.put(
            f"/alertas/{alerta_id}",
            headers=admin_headers,
            json={"status": "ignorado", "mensagem": "Ignorado via teste"},
        )
    )
    assert ignored["status"] == "ignorado"

    medicamento_id = assert_json_response(client.get("/medicamentos?busca=Paracetamol", headers=admin_headers))[0]["id"]
    unidade_id = assert_json_response(client.get("/unidades?busca=UBS", headers=admin_headers))[0]["id"]
    created = assert_json_response(
        client.post(
            "/alertas",
            headers=admin_headers,
            json={
                "tipo": "estoque_baixo",
                "mensagem": "Alerta manual de teste",
                "status": "ativo",
                "medicamento_id": medicamento_id,
                "unidade_id": unidade_id,
            },
        )
    )
    assert created["mensagem"] == "Alerta manual de teste"


def test_configuracoes_alerta_crud(client, admin_headers, funcionario_headers):
    configs = assert_json_response(client.get("/configuracoes-alerta", headers=funcionario_headers))
    assert len(configs) == 1  # RN07: funcionário consulta apenas sua unidade.

    config_id = configs[0]["id"]
    updated = assert_json_response(
        client.put(
            f"/configuracoes-alerta/{config_id}",
            headers=admin_headers,
            json={"dias_alerta_vencimento": 45, "notificacao_email": True},
        )
    )
    assert updated["dias_alerta_vencimento"] == 45
    assert updated["notificacao_email"] is True

    nova_unidade = assert_json_response(client.post("/unidades", headers=admin_headers, json={"nome": "Unidade Config"}))
    created = assert_json_response(
        client.post(
            "/configuracoes-alerta",
            headers=admin_headers,
            json={"unidade_id": nova_unidade["id"], "estoque_minimo_padrao": 8},
        )
    )
    assert created["estoque_minimo_padrao"] == 8
    assert client.post("/configuracoes-alerta", headers=funcionario_headers, json=created).status_code == 403


def test_dashboard_and_reports(client, admin_headers, funcionario_headers):
    dashboard = assert_json_response(client.get("/dashboard", headers=admin_headers))
    assert dashboard["totalMedicamentos"] >= 3
    assert dashboard["alertasAtivos"] >= 1
    assert "movimentacoesRecentes" in dashboard
    assert "alertasRecentes" in dashboard

    relatorio = assert_json_response(client.get("/relatorios/movimentacoes", headers=admin_headers))
    assert relatorio["resumo"]["total_movimentacoes"] >= 0
    assert isinstance(relatorio["resultados"], list)

    csv_response = client.get("/relatorios/movimentacoes.csv", headers=admin_headers)
    assert csv_response.status_code == 200
    assert csv_response.headers["content-type"].startswith("text/csv")
    assert "id,tipo,medicamento" in csv_response.text

    critico_admin = assert_json_response(client.get("/relatorios/estoque-critico", headers=admin_headers))
    assert critico_admin
    assert any(item["estoque_baixo"] or item["vencido"] for item in critico_admin)

    critico_funcionario = assert_json_response(client.get("/relatorios/estoque-critico", headers=funcionario_headers))
    assert all(item["unidade"] == "UBS Centro" for item in critico_funcionario)
    assert client.get("/relatorios/movimentacoes?tipo=errado", headers=admin_headers).status_code == 400
