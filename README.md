# Remédio Certo

Aplicativo Android para controle de estoque de medicamentos em unidades de saúde. Registra entradas, saídas, descartes e ajustes por lote, alerta sobre estoque baixo e vencimentos e continua funcionando sem internet. A sincronização acontece quando a conexão volta.

O repositório reúne o **app Android** (Kotlin e Jetpack Compose) e a **API REST** (FastAPI) que ele consome.

> Projeto acadêmico do curso de Análise e Desenvolvimento de Sistemas, disciplina de Programação para Dispositivos Móveis.

---

## Funcionalidades

**Acesso**

- Login com perfis de administrador e funcionário; o funcionário opera apenas a própria unidade.
- Sessão salva no aparelho, com renovação automática do token (refresh token) e logout encerrando a sessão na API.
- Troca de senha.

**Estoque e movimentações**

- Painel inicial com indicadores e movimentações recentes.
- Estoque agrupado por medicamento e unidade, com busca, filtros por situação e detalhe por lote.
- Entrada, saída, descarte e ajuste, com leitura de código de barras e sugestão do lote que vence primeiro (FEFO).
- Histórico de movimentações.

**Alertas**

- Alertas de estoque baixo, vencimento próximo e lote vencido, nas abas Ativos, Resolvidos e Ignorados.
- Ação recomendada para cada tipo de alerta e destaque para os casos críticos.
- Configuração por unidade: estoque mínimo padrão, dias de aviso de vencimento e dias para considerar crítico.

**Relatórios e cadastros**

- Relatórios em PDF e CSV, com compartilhamento pelo aparelho.
- Cadastros de medicamentos, unidades de saúde e usuários (administrador).

**Modo offline**

- O estoque consultado fica salvo no aparelho e é exibido quando não há conexão.
- Movimentações feitas sem internet entram numa fila local e são enviadas automaticamente quando a conexão volta, mesmo com o app fechado.
- Cada movimentação leva um identificador único, e a API nunca aplica a mesma operação duas vezes em caso de reenvio. As que a API recusar aparecem no app com o motivo.

## Regras de negócio

| Regra | Descrição                                                                         |
| ----- | --------------------------------------------------------------------------------- |
| RN01  | O saldo de um lote nunca fica negativo.                                           |
| RN03  | Lote vencido não aceita entrada nem saída comum, apenas descarte.                 |
| RN04  | Estoque baixo é calculado pelo total do medicamento na unidade, somando os lotes. |
| RN05  | O alerta é crítico a partir dos dias configurados para a unidade (padrão: 7).     |
| RN06  | Ajuste de estoque exige justificativa.                                            |
| FEFO  | Na saída, o lote sugerido é o que vence primeiro.                                 |

As regras são validadas no app, inclusive no modo offline, e novamente na API.

## Tecnologias

| Camada            | Tecnologias                                                                          |
| ----------------- | ------------------------------------------------------------------------------------ |
| App Android       | Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel                   |
| Comunicação       | Retrofit, OkHttp, kotlinx.serialization                                              |
| Dados no aparelho | DataStore (sessão), Room (estoque salvo e fila offline), WorkManager (sincronização) |
| Leitura de código | Google Code Scanner (Play Services)                                                  |
| API               | Python 3.12, FastAPI, SQLAlchemy, Pydantic, JWT                                      |
| Banco de dados    | SQLite em desenvolvimento; PostgreSQL ou MySQL em produção                           |
| Qualidade         | JUnit (app), pytest (API), GitHub Actions                                            |

## Estrutura do repositório

```
.
├── RemedioCerto/            # Projeto Android (abrir esta pasta no Android Studio)
│   └── app/src/
│       ├── main/java/br/com/remediocerto/
│       │   ├── core/        # rede, sessão, navegação, banco local, offline, componentes de UI
│       │   └── feature/     # telas: auth, dashboard, estoque, movimentacao, alertas, cadastros...
│       └── test/            # testes unitários
├── backend/                 # API FastAPI (detalhes em backend/README.md)
├── docs/                    # deploy e gestão do projeto
├── .github/workflows/       # integração contínua da API
└── render.yaml              # configuração de hospedagem
```

## Como executar

### Pré-requisitos

- Android Studio atualizado (o JDK necessário já vem com ele)
- Python 3.12
- Emulador Android ou aparelho com depuração USB ativada

### 1. Subir a API

Na raiz do repositório, pelo PowerShell:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
Copy-Item .env.example .env
# Edite o .env e defina SECRET_KEY com um valor próprio e aleatório
.\.venv\Scripts\python.exe -m app.init_db
.\.venv\Scripts\python.exe -m app.create_admin
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

O comando `create_admin` cria o administrador inicial: responda às perguntas (senha de 12 a 120 caracteres) ou defina `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NOME` e `BOOTSTRAP_ADMIN_PASSWORD` no ambiente.

Com a API no ar:

- Documentação interativa: <http://127.0.0.1:8000/docs>
- Verificação de saúde: `GET /health`

Contratos, variáveis de ambiente e dados de demonstração estão em [`backend/README.md`](backend/README.md).

### 2. Rodar o app

1. Abra a pasta `RemedioCerto/` no Android Studio e aguarde a sincronização do Gradle.
2. Inicie o emulador (ou conecte o aparelho) e libere o acesso à API local:
   ```
   adb reverse tcp:8000 tcp:8000
   ```
3. Clique em **Run**.

O endereço da API fica em `core/network/ApiClient.kt` (`BASE_URL`). Com o `adb reverse`, o padrão `http://127.0.0.1:8000/` funciona no emulador e no aparelho via USB. Para usar a API hospedada, troque pelo endereço HTTPS dela.

### 3. Gerar o APK

No Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Ou pelo terminal, dentro de `RemedioCerto/`:

```powershell
.\gradlew assembleDebug
```

O arquivo é gerado em `RemedioCerto/app/build/outputs/apk/debug/`.

## Testes

**App**: dentro de `RemedioCerto/`:

```powershell
.\gradlew test
```

Cobre as regras de estoque e movimentação, formatação de datas, mensagens de erro, regras e configuração de alertas e as conversões do modo offline.

**API**: dentro de `backend/`, com o ambiente virtual criado:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Os testes da API também rodam no GitHub Actions a cada push, em SQLite e PostgreSQL.

## Deploy

O [guia de deploy](docs/deploy.md) descreve a publicação da API com Docker e Render (HTTPS e PostgreSQL), as credenciais externas e a verificação após a publicação. A configuração está em [`render.yaml`](render.yaml).

Em produção, a API exige `SECRET_KEY` forte e banco persistente. O arquivo `.env` nunca deve ir para o repositório.

## Como contribuir

1. Atualize a `main` e crie uma branch para a tarefa: `feature/nome-da-tarefa`.
2. Implemente, rode os testes e faça commits pequenos e descritivos.
3. Envie a branch e abra um Pull Request para a `main`.
4. O merge exige revisão de um colega e os testes da integração contínua aprovados.

Não faça commit direto na `main`.

## Equipe

| Integrante                             | Papel                                       |
| -------------------------------------- | ------------------------------------------- |
| Flávia Tavares do Nascimento           | Qualidade, testes e desenvolvimento Android |
| Luís Gustavo Rocha Gomes de Andrade    | Líder do projeto e backend                  |
| Luiz Carlos Souza Costa Cavadinha      | UI/UX e documentação                        |
| Silmara Pereira de Lima                | Desenvolvimento Android                     |
| Paulo Henrique Freire de Matos Barbosa | Desenvolvimento Android                     |

## Licença

Distribuído sob a licença MIT. Veja o arquivo [`LICENSE`](LICENSE).
