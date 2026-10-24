# Deploy da API

A API fica em `backend/`, importada do backend existente de [LCCavadinha/remedio-certo-app, referência 968b3eb](https://github.com/LCCavadinha/remedio-certo-app/tree/968b3eb/remedio_certo_backend/remedio_certo_fastapi_mysql). O deploy deve usar esta cópia atualizada no repositório Android. Não há necessidade de copiar ou publicar o frontend web de origem.

## Configuração

| Variável | Uso |
| --- | --- |
| `ENVIRONMENT` | `production` no ambiente publicado; desenvolvimento local usa a configuração do `.env.example`. |
| `SECRET_KEY` | Segredo próprio do JWT; o Blueprint gera um valor aleatório. Não reutilizar o valor de exemplo. |
| `DATABASE_URL` | PostgreSQL publicado; SQLite local; MySQL continua disponível no backend herdado. Nunca usar um arquivo SQLite efêmero como banco de nuvem. |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | `15`. |
| `REFRESH_TOKEN_EXPIRE_DAYS` | `7`. |
| `RESEND_API_KEY`, `EMAIL_FROM` | Envio de recuperação de senha por HTTPS, com remetente autorizado no provedor. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS` | Alternativa SMTP em hospedagem que permita tráfego nessas portas. |
| `FIREBASE_CREDENTIALS_JSON` | JSON da conta de serviço do projeto Firebase correto, armazenado como segredo. Nunca versionar esse arquivo. |
| `ALERTS_ENABLED`, `ALERTS_INTERVAL_SECONDS` | Habilitação e intervalo do agendador; o exemplo usa `true` e `60`. |
| `TIMEZONE` | `America/Sao_Paulo`. |

O `.env.example` e o código de configuração do backend são a referência dos valores opcionais e padrões. O código não precisa de credenciais externas para executar os testes automatizados com serviços simulados.

## Render com HTTPS e PostgreSQL

O [`render.yaml`](../render.yaml) prepara um Web Service Docker e um PostgreSQL na mesma região. A presença do arquivo não significa que a API esteja publicada. O responsável precisa de uma conta Render e dos projetos de e-mail/Firebase.

1. Conecte o repositório `Silmara-Lima/remedio-certo-android` no Render e crie um Blueprint a partir da branch aprovada para homologação. Confira os recursos antes de confirmar. O exemplo usa os planos gratuitos e desativa deploys automáticos.
2. Preencha `RESEND_API_KEY` e `EMAIL_FROM` de um remetente autorizado. Preencha `FIREBASE_CREDENTIALS_JSON` exclusivamente no ambiente do provedor. `SECRET_KEY` é gerado pelo Render e `DATABASE_URL` vem do PostgreSQL.
3. Faça o deploy manual. O contêiner executa `python -m app.init_db` para criar tabelas ausentes antes de iniciar a API. O comando é idempotente e não apaga tabelas existentes; alterações futuras de colunas em bancos já usados exigem migração planejada.
4. Confira `GET /health` na URL HTTPS real fornecida pelo Render. Verifique a documentação `/docs` e os logs sem expor credenciais.
5. Crie o administrador inicial conforme a seção seguinte. Registre a URL, a data de criação/expiração do banco e os resultados dos testes no cartão LG06. Combine com a equipe Android a URL publicada e o teste do APK.

O exemplo é para homologação. Render Free suspende serviços ociosos, bloqueia SMTP nas portas usuais e oferece PostgreSQL gratuito que expira em 30 dias. Por isso, o exemplo utiliza e-mail por HTTPS. O agendador de alertas só executa enquanto a API está ativa. Para notificações periódicas com prazo garantido, a equipe precisa acordar hospedagem sempre ativa ou agendamento externo e persistência adequada. Confira os [limites oficiais](https://render.com/docs/free) antes de criar os recursos.

A [referência oficial do Blueprint](https://render.com/docs/blueprint-spec) documenta `autoDeployTrigger: off`, geração de segredos e conexão ao PostgreSQL. A API recebe a porta pelo `PORT` do provedor e roda com um worker, evitando duplicar o agendador deste MVP.

## Primeiro administrador

Use `python -m app.create_admin` em um terminal autorizado conectado ao banco correto. Responda aos prompts (senha de 12 a 120 caracteres) ou defina `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NOME` e `BOOTSTRAP_ADMIN_PASSWORD` no ambiente. Não há necessidade de publicar uma senha de administrador no código. Remova as variáveis de bootstrap após criar o usuário.

No Render Free, que não disponibiliza shell do serviço, é possível executar o comando localmente: use a URL externa do banco como `DATABASE_URL` e permita temporariamente apenas o IP do computador autorizado no controle de acesso do banco. Não libere acesso irrestrito. Execute a criação e remova a liberação temporária e as credenciais locais após o uso.

## Docker e outro provedor

Na raiz do repositório:

```powershell
docker build -t remedio-certo-api backend
docker run --rm --env-file backend/.env -p 8000:8000 remedio-certo-api
```

O arquivo `.env` fica fora da imagem. O contêiner usa usuário sem privilégios e porta 8000 por padrão. SQLite no contêiner é somente para teste descartável: remover o contêiner elimina seus dados. Para hospedagem use PostgreSQL, segredos do provedor e HTTPS na entrada. O mesmo Dockerfile pode ser usado no Railway ou em outro serviço compatível, configurando `DATABASE_URL`, `SECRET_KEY`, credenciais de e-mail/Firebase e `PORT`.

## Verificação de homologação

Registre resultado e evidência de cada item antes de marcar LG06 como concluído:

- `/health` saudável na URL HTTPS, após reinício e novo deploy; dados mantidos no PostgreSQL.
- Login, `/auth/me`, renovação após expiração do access token, logout e rejeição do refresh revogado.
- E-mail de recuperação entregue em uma caixa de teste, código/token usado uma vez e expirado recusado.
- Token FCM de aparelho real registrado; alerta gerado envia push; toque abre o destino implementado pela equipe Android.
- Lote offline reenviado sem duplicar movimentações; saldo insuficiente e unidade não autorizada retornam conflito/erro esperado.
- APK aponta para HTTPS e conserva os fluxos já entregues; backup/restauração e expiração do banco registrados.

Nenhum teste externo, criação de conta ou publicação é presumido como realizado por este guia.
