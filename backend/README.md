# Remedio Certo - Backend FastAPI

Backend existente em Python com **FastAPI** e **SQLAlchemy**, copiado de [LCCavadinha/remedio-certo-app](https://github.com/LCCavadinha/remedio-certo-app/tree/968b3eb/remedio_certo_backend/remedio_certo_fastapi_mysql), referencia `968b3eb`, pasta `remedio_certo_backend/remedio_certo_fastapi_mysql`. As novas funcionalidades foram adicionadas a esta base; o aplicativo Android e o frontend web de origem nao foram reescritos.

## O que foi usado

- **FastAPI**: framework da API.
- **SQLAlchemy**: conversa com o banco de dados.
- **Pydantic**: valida os dados que entram e saem da API.
- **JWT**: token de login.
- **Passlib/Bcrypt**: hash de senha.
- **SQLite** por padrao para estudar rapido.
- **MySQL** opcional via `DATABASE_URL`, como na base existente.
- **PostgreSQL** via `DATABASE_URL` para a hospedagem preparada nesta entrega.

## Como rodar

Entre nesta pasta:

```powershell
cd backend
```

Crie e ative o ambiente virtual:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

Instale as dependencias:

```powershell
pip install -r requirements.txt
```

Copie `.env.example` para `.env` e defina `SECRET_KEY` com um segredo proprio. Crie as tabelas e o primeiro administrador:

```powershell
python -m app.init_db
# Responda aos prompts ou configure BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_NOME e BOOTSTRAP_ADMIN_PASSWORD.
python -m app.create_admin
```

A senha do administrador deve ter de 12 a 120 caracteres. Remova a senha de bootstrap apos o uso. Para testes automatizados, instale `requirements-dev.txt` e execute `python -m pytest -q`.

Rode a API:

```powershell
python run.py
```

Acesse:

- API: <http://127.0.0.1:8000>
- Documentacao: <http://127.0.0.1:8000/docs>

## Dados ficticios de demonstracao

O script herdado `python scripts\seed.py` cria unidades, usuarios e medicamentos ficticios com credenciais de demonstracao conhecidas. Use-o somente em banco local descartavel, em desenvolvimento. Nao o execute na hospedagem nem em banco com dados reais. Para homologacao, crie o administrador com `python -m app.create_admin` e senha propria.

## Como usar MySQL

1. Crie o banco:

```powershell
Get-Content .\criar_banco_mysql.sql | mysql -u root -p
```

2. Copie `.env.example` para `.env`.
3. No `.env`, troque a linha `DATABASE_URL` para:

```text
DATABASE_URL="mysql+pymysql://root:sua_senha@localhost:3306/remedio_certo?charset=utf8mb4"
```

4. Rode:

```powershell
python -m app.init_db
python -m app.create_admin
python run.py
```

## Estrutura do projeto

```text
app/
  main.py                 cria a aplicacao FastAPI e registra as rotas
  config.py               le configuracoes do .env
  database.py             abre conexao com o banco
  models.py               tabelas do banco
  schemas.py              formatos de entrada e saida da API
  security.py             senha e token JWT
  dependencies.py         usuario logado e permissao de admin
  services.py             regras automaticas, como alertas
  routers/                rotas separadas por modulo

scripts/
  seed.py                 cria dados iniciais
  gerar_hash.py           gera hash de senha manualmente
```

## Rotas principais

```text
POST /auth/login
GET  /auth/me
PUT  /auth/trocar-senha

GET/POST/PUT/DELETE /unidades
GET/POST/PUT/DELETE /usuarios
GET/POST/PUT/DELETE /medicamentos
GET/POST/PUT/DELETE /estoques

GET/POST /movimentacoes
GET/POST/PATCH /alertas
GET /dashboard
GET /relatorios/movimentacoes
GET /relatorios/movimentacoes.csv
GET /relatorios/estoque-critico
```

## Contrato usado pelo frontend

Estas rotas foram alinhadas com o documento de integracao enviado pelo Front:

```text
GET /auth/me
GET /dashboard
GET /estoque?unidade={id}
GET /movimentacoes?unidadeId={id}&tipo={tipo}&dataInicio={data}&dataFim={data}
GET /relatorios/movimentacoes?unidadeId={id}&dataInicio={data}&dataFim={data}
GET /alertas/estoque-baixo
GET /alertas/validade
```

O `/auth/me` retorna:

```json
{
  "id": 1,
  "nome": "Administrador",
  "email": "admin@remediocerto.com",
  "tipo": "ADMIN",
  "ativo": true,
  "unidadeSaudeId": 1
}
```

O `/dashboard` retorna pelo menos:

```json
{
  "totalMedicamentos": 0,
  "alertasAtivos": 0,
  "movimentacoesHoje": 0,
  "estoqueCritico": 0
}
```

Os endpoints de alertas retornam itens assim:

```json
{
  "id": 1,
  "tipo": "ESTOQUE_BAIXO",
  "mensagem": "Medicamento X abaixo do estoque minimo",
  "status": "PENDENTE",
  "dataGeracao": "2026-05-23T10:00:00",
  "dataResolucao": null,
  "medicamentoId": 1,
  "unidadeSaudeId": 1
}
```

## Regra de estoque

- `entrada`: soma quantidade ao lote. Se o lote nao existir, cria.
- `saida`: diminui quantidade. Bloqueia se nao houver estoque suficiente.
- `descarte`: diminui quantidade, usado para vencidos ou perdas.
- `ajuste`: define a quantidade final do lote.

Depois de uma movimentacao, o sistema recalcula alertas de estoque baixo, vencimento proximo e vencido.


## Novas funcionalidades do backend

A extensao preserva as rotas e os campos usados pelo aplicativo atual. O login continua retornando `access_token`, `token_type` e `usuario`; a sessao passa a incluir refresh token e duracoes. Access tokens duram 15 minutos e refresh tokens duram 7 dias, conforme configuracao.

- Renovacao de sessao com rotacao do refresh token e revogacao no logout. O cliente deve salvar o refresh novo a cada renovacao; reenvio do refresh antigo e recusado e revoga a familia. O prazo de sete dias e absoluto desde o login; cada rotacao nao prorroga esse limite.
- Recuperacao de senha por e-mail com token de uso unico e validade de 15 minutos. O envio pode usar SMTP com TLS ou Resend por HTTPS. A redefinicao de senha invalida todas as sessoes. A troca autenticada preserva a sessao corrente para manter o fluxo atual do app e revoga as demais.
- Registro e remocao do token FCM do aparelho, vinculados ao usuario autenticado; alertas gerados entram em uma fila persistente de envio. Credenciais Firebase reais sao configuradas no ambiente.
- Sincronizacao de movimentacoes offline em lote, com identificador local estavel, revalidacao de saldo no servidor e resposta individual por operacao. Reenviar uma operacao ja aceita nao deve duplicar o estoque.

Os detalhes, campos e respostas completos ficam no OpenAPI em `/docs`. Os novos endpoints precisam ser consumidos pela equipe Android: Silmara coordena sessao/recuperacao, Paulo a fila offline e Flavia o registro do aparelho e o destino do push. A existencia dos endpoints nao habilita essas telas ou fluxos automaticamente no APK atual.

## Hospedagem e banco

Veja o [guia de deploy](../docs/deploy.md) para Docker, Render HTTPS/PostgreSQL, bootstrap e verificacao. `python -m app.init_db` cria tabelas ausentes de forma idempotente sem apagar as existentes. Ele nao substitui uma migracao de colunas em banco existente; mudancas futuras de esquema exigem planejamento e backup.

No PostgreSQL use `postgresql+psycopg://usuario:senha@host:5432/banco` em `DATABASE_URL`; a URL recebida do Render tambem e aceita pela configuracao do backend. SQLite continua sendo o padrao para desenvolvimento e MySQL continua disponivel. Nenhum dado do banco existente e copiado automaticamente para o novo ambiente.

A configuracao de exemplo nao comprova publicacao. A entrega de e-mail, o push em aparelho, o acesso HTTPS e o fluxo offline completo precisam ser homologados com as contas e o APK da equipe.


### Contratos de sessao e recuperacao

| Metodo e rota | Autenticacao / corpo | Resultado |
| --- | --- | --- |
| `POST /auth/login` | `{ "email": "usuario@exemplo.com", "senha": "senha-do-usuario" }` | Campos atuais mais `refresh_token`, `expires_in` e `refresh_expires_in`. |
| `POST /auth/refresh` | `{ "refresh_token": "refresh-recebido-no-login" }` | Novo access e refresh; substituir os dois tokens armazenados. |
| `POST /auth/logout` | `Authorization: Bearer <access_token>` | Revoga a familia da sessao corrente. |
| `POST /auth/recuperar-senha` | `{ "email": "usuario@exemplo.com" }` | Mensagem generica; envia codigo aleatorio por e-mail se a conta existir. Sem servico de e-mail configurado retorna `503`. |
| `POST /auth/redefinir-senha` | `{ "token": "codigo-recebido-no-email", "nova_senha": "nova-senha-do-usuario" }` | Consome o codigo de uso unico, troca a senha e revoga as sessoes. |
| `PUT /auth/trocar-senha` | Bearer e corpo atual `{ "senha_atual": "senha-atual", "nova_senha": "nova-senha" }` | Preserva a sessao corrente; revoga as outras. |

O codigo de recuperacao nao e um link de pagina web: deve ser informado no fluxo de redefinicao implementado no Android. Expira em 15 minutos. Nunca registrar codigo de recuperacao, senhas ou tokens nos logs.

### Contrato de dispositivos

`POST /dispositivos` registra ou atualiza o token FCM com Bearer e corpo `{ "token": "token-fcm-do-aparelho" }`. `DELETE /dispositivos` remove o token do usuario autenticado, com o mesmo corpo. O app deve registrar novamente quando o Firebase renovar o token e remover a associacao no logout. O toque na notificacao depende do destino implementado por Flavia no Android.


### Contrato de sincronizacao em lote

`POST /movimentacoes/lote` exige Bearer e recebe de 1 a 100 movimentacoes. Cada item tem os campos de `POST /movimentacoes` mais `id_local` (UUID gerado e persistido no aparelho antes do primeiro envio). Exemplo de entrada; substitua IDs, lote e validade pelos dados reais do ambiente:

```json
{
  "movimentacoes": [
    {
      "id_local": "91d579af-4f3b-43c5-8e7e-f83f53fc64c8",
      "tipo": "entrada",
      "medicamento_id": 1,
      "unidade_id": 1,
      "lote": "EXEMPLO-001",
      "validade": "2030-12-31",
      "quantidade": 10,
      "observacao": "Recebimento registrado offline"
    }
  ]
}
```

A resposta tem `resultados`, na ordem de envio. Cada resultado inclui `id_local`, `status` (`aceito` ou `rejeitado`) e `repetida`; quando aceito inclui `movimentacao`, e quando rejeitado inclui `codigo` e `mensagem`. Uma resposta HTTP bem-sucedida pode conter rejeicoes individuais; o cliente deve analisar todos os resultados antes de remover itens da fila.

O mesmo usuario deve reenviar o mesmo UUID com os mesmos dados apos uma falha de rede. Uma operacao ja processada retorna seu resultado com `repetida: true`, sem aplicar o saldo novamente. Reutilizar o UUID com dados diferentes retorna `ID_LOCAL_REUTILIZADO`. Um resultado rejeitado e registrado: o cliente deve apresenta-lo para revisao, em vez de tentar automaticamente como nova operacao. Uma correcao deliberada recebe um novo UUID. Saldo, validade, unidade e permissao sao revalidados no servidor; a data de registro do servidor permanece a referencia da movimentacao.

### Dados do push

A mensagem e do tipo data; o app Android precisa recebe-la e criar a notificacao visivel. A entrega admite repeticao, portanto o cliente deve deduplicar por `alerta_id`. O payload identifica o destino com campos de texto `alerta_id`, `unidade_id`, `tipo` e `deep_link` (`remediocerto://alertas/{id}`). A equipe Android deve autenticar a sessao e verificar o acesso ao alerta antes de exibir seus dados. A fila persistente tenta novamente falhas temporarias e remove registros FCM expirados; sem credenciais Firebase configuradas, o envio externo fica pendente.
