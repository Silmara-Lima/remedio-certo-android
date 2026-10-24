# Remédio Certo

Aplicativo Android em Kotlin e Jetpack Compose para estoque de medicamentos. O projeto existente está em [`RemedioCerto/`](RemedioCerto/) e deve ser aberto no Android Studio.

A API FastAPI está em [`backend/`](backend/). Ela foi copiada do backend existente em [`LCCavadinha/remedio-certo-app`](https://github.com/LCCavadinha/remedio-certo-app/tree/968b3eb/remedio_certo_backend/remedio_certo_fastapi_mysql), referência `968b3eb`, e ampliada neste repositório com refresh token, recuperação de senha por e-mail, registro de dispositivos/push e sincronização em lote. O frontend web daquele repositório não faz parte desta entrega. O projeto Android existente foi preservado.

## Executar a API

Requer Python 3.12. Na raiz do repositório, usando PowerShell:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
Copy-Item .env.example .env
# Defina SECRET_KEY em .env com um segredo próprio antes de executar.
.\.venv\Scripts\python.exe -m app.init_db
.\.venv\Scripts\python.exe -m app.create_admin
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

Para criar o administrador inicial, responda aos prompts do comando (senha de 12 a 120 caracteres) ou configure `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NOME` e `BOOTSTRAP_ADMIN_PASSWORD` no ambiente. Remova a senha de bootstrap após o uso e mantenha `.env` fora do Git. O seed de demonstração herdado é apenas para desenvolvimento com dados fictícios.

Documentação local: <http://127.0.0.1:8000/docs>. Disponibilidade: `GET /health`. Para testar, execute `.\.venv\Scripts\python.exe -m pytest -q` dentro de `backend/`. Veja a [documentação da API](backend/README.md) para contratos e configuração.

O app aponta atualmente para `http://127.0.0.1:8000/`. Para usar o servidor local no aparelho/emulador conectado, execute `adb reverse tcp:8000 tcp:8000`. A integração dos novos recursos e da URL HTTPS é coordenada com a equipe Android.

## Hospedagem e acompanhamento

O [guia de deploy](docs/deploy.md) explica Docker, Render HTTPS/PostgreSQL, credenciais externas, bootstrap e verificação. A configuração está em [`render.yaml`](render.yaml); os serviços ainda precisam ser criados e homologados na conta do responsável.

O [quadro, marcos, rituais e riscos](docs/gestao.md) define o acompanhamento das entregas. O workflow [Backend](.github/workflows/backend.yml) executa testes em SQLite e PostgreSQL e constrói a imagem Docker. Envio real de e-mail, push e instalação do APK exigem homologação com os serviços e aparelhos da equipe.

Fluxo de contribuição: atualizar `main`, criar `feature/nome-da-tarefa`, implementar e testar, enviar a branch e abrir Pull Request. O merge exige revisão de um colega e CI aprovado. Não fazer commit direto em `main`.
