# Plano de Testes – Remédio Certo (Android)

**Versão:** 1.1 – 02/11/2026 (atualizada após a integração das branches na `main`)
**Escopo:** aplicativo Android (Kotlin + Jetpack Compose) e sua integração com a API v1.

## 1. Objetivo

Garantir que o aplicativo atende aos requisitos Must e Should do Plano de Projeto, que as regras de negócio RN01 a RN09 estão comprovadas e que os fluxos A a D funcionam do começo ao fim antes da entrega do APK.

## 2. Níveis de teste

| Nível              | O que cobre                                                    | Como roda                                                              | Meta                                |
| ------------------ | -------------------------------------------------------------- | ---------------------------------------------------------------------- | ----------------------------------- |
| Unitário (app)     | Regras de negócio, formatação, agrupamento de estoque, alertas | `./gradlew testDebugUnitTest` ou botão direito > Run no Android Studio | Todas as regras RN com teste        |
| Unitário (API)     | Endpoints, autorização, saldo, sincronização                   | `pytest -q` em `backend/`                                              | Todos os endpoints                  |
| Manual / funcional | Casos CT deste plano, tela a tela                              | Emulador e celular físico, roteiro abaixo                              | 100% dos casos Must aprovados       |
| Usabilidade        | Tarefas cronometradas + questionário SUS (com Luiz Carlos)     | 5 usuários, roteiro de tarefas                                         | SUS ≥ 75; saída pelo scanner ≤ 30 s |

## 3. Ambiente

- **Emulador:** Medium Phone, API 37 (Android 17).
- **Celular físico:** pelo menos 1 aparelho Android real (obrigatório para o scanner, CT04, e para o push, CT07).
- **API:** local (`http://127.0.0.1:8000`, com `adb reverse tcp:8000 tcp:8000`) ou a URL hospedada pelo Luís.
- **Dados:** seed de demonstração do backend (`scripts/seed.py`), somente dados fictícios.
- **Usuários de teste (seed):**
  - Administrador: `admin@remediocerto.com`
  - Funcionária da UBS Centro: `maria.silva@remediocerto.com`
  - Funcionário do Hospital São Lucas: `joao.santos@remediocerto.com`
  - Senha de todos no seed: ver `backend/scripts/seed.py` (não usar fora do ambiente de teste).

## 4. Critérios de entrada e saída

**Entrada:** funcionalidade com Pull Request aberto, build compilando e testes unitários verdes.

**Saída (aceite do MVP):**

- 100% dos casos ligados a requisitos Must aprovados;
- nenhum defeito crítico ou alto aberto;
- todos os testes unitários verdes;
- fluxos A, B, C e D executados com sucesso.

## 5. Classificação de defeitos

| Gravidade | Exemplo                                                   |
| --------- | --------------------------------------------------------- |
| Crítico   | App fecha sozinho; saldo fica negativo; movimentação some |
| Alto      | Funcionalidade Must não funciona, sem alternativa         |
| Médio     | Funciona com alternativa, ou mensagem errada              |
| Baixo     | Texto, alinhamento, cor                                   |

Defeitos são registrados como issue no GitHub com: passos para reproduzir, resultado esperado, resultado obtido, print e aparelho usado.

## 6. Casos de teste

Legenda de situação: ✅ Passou · ❌ Falhou · ⏳ Não executado · 🚫 Bloqueado (depende de outra entrega)

### 6.1 Autenticação e acesso

| Código | Requisito   | Cenário                             | Passos                                                                                                                                              | Resultado esperado                                                                                            | Situação                                 |
| ------ | ----------- | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| CT01   | RF01        | Login com credenciais válidas       | Informar e-mail e senha do admin > Entrar                                                                                                           | Abre a aba Início com o perfil Administrador                                                                  | ✅ 02/11 (emulador)                      |
| CT02   | RF01        | Login com senha incorreta           | Informar senha errada > Entrar                                                                                                                      | Fica no login com mensagem clara; não entra                                                                   | ⏳                                       |
| CT13   | RF01        | Renovação automática da sessão      | Fazer login, esperar o token de acesso vencer (15 min; para testar mais rápido, `ACCESS_TOKEN_EXPIRE_MINUTES=1` no `backend/.env`) e usar o app     | O app continua funcionando sem pedir login; no Logcat aparece `POST /auth/refresh` com 200                    | ⏳                                       |
| CT47   | RF01        | Sessão encerrada                    | Com o token de 1 min: logar no app; pelo `/docs`, logar com o mesmo usuário e trocar a senha (revoga as outras sessões); esperar 1 min e usar o app | Volta para o login com o aviso "Sua sessão expirou"                                                           | ⏳                                       |
| CT48   | RF01        | Sair encerra a sessão na API        | Mais > Sair                                                                                                                                         | Volta ao login; no Logcat aparece `POST /auth/logout` com 200                                                 | ⏳                                       |
| CT14   | RF03        | Trocar senha                        | Mais > Trocar senha > senha atual + nova                                                                                                            | Senha alterada; login antigo deixa de funcionar                                                               | ⏳                                       |
| CT15   | RF03        | Recuperar senha                     | Login > Esqueci a senha                                                                                                                             | Código chega por e-mail e permite definir nova senha                                                          | 🚫 tela no app e e-mail da API pendentes |
| CT03   | RF02        | Login por biometria                 | Habilitar biometria > sair > entrar pela digital                                                                                                    | Entra sem digitar senha                                                                                       | 🚫 só se o grupo mantiver a biometria    |
| CT10   | RF04 / RN07 | Funcionário tenta acessar cadastros | Entrar como Maria > aba Mais                                                                                                                        | Botões de Medicamentos, Unidades, Usuários e Configuração de alertas não aparecem; API retorna 403 se chamada | ⏳                                       |
| CT16   | RN07        | Funcionário vê só a própria unidade | Entrar como Maria > Estoque e Alertas                                                                                                               | Só aparecem itens da UBS Centro                                                                               | ⏳                                       |

### 6.2 Cadastros (Administrador)

| Código | Requisito | Cenário                   | Passos                                          | Resultado esperado                            | Situação |
| ------ | --------- | ------------------------- | ----------------------------------------------- | --------------------------------------------- | -------- |
| CT17   | RF05      | Cadastrar medicamento     | Mais > Medicamentos > Novo > preencher > Salvar | Medicamento aparece na lista                  | ⏳       |
| CT18   | RF05      | Desativar medicamento     | Editar > Desativar                              | Some da lista de ativos; histórico preservado | ⏳       |
| CT19   | RF06      | Unidade com CNPJ inválido | Nova unidade > CNPJ com 10 dígitos > Salvar     | Mensagem "O CNPJ deve ter 14 dígitos."        | ⏳       |
| CT20   | RF07      | Cadastrar usuário         | Mais > Usuários > Novo > perfil e unidade       | Usuário consegue entrar com a senha definida  | ⏳       |

### 6.3 Estoque e movimentações

| Código | Requisito   | Cenário                           | Passos                                                  | Resultado esperado                                                          | Situação            |
| ------ | ----------- | --------------------------------- | ------------------------------------------------------- | --------------------------------------------------------------------------- | ------------------- |
| CT21   | RF08        | Consultar estoque com filtro      | Estoque > filtro "Vencido"                              | Só aparecem medicamentos com lote vencido, com selo e cor                   | ⏳                  |
| CT04   | RF09        | Escanear medicamento cadastrado   | Escanear > apontar para o código de barras              | Abre Nova movimentação com medicamento e lote FEFO já selecionados          | ⏳ (celular físico) |
| CT22   | RF09        | Escanear código não cadastrado    | Escanear código desconhecido                            | Aviso de que não existe e opção de cadastro (admin)                         | ⏳                  |
| CT05   | RF10        | Saída com saldo suficiente        | Saída de 1 unidade de um lote com saldo                 | Movimentação registrada; saldo diminui 1                                    | ⏳                  |
| CT06   | RF10 / RN01 | Saída maior que o saldo           | Saída de quantidade maior que o lote                    | Bloqueada, mostrando o saldo disponível                                     | ⏳                  |
| CT23   | RN02        | Sugestão FEFO                     | Nova saída de medicamento com 2 lotes                   | O lote que vence primeiro vem selecionado                                   | ⏳                  |
| CT24   | RN03        | Saída de lote vencido             | Tentar saída comum de lote vencido                      | Bloqueada; só descarte é permitido                                          | ⏳                  |
| CT25   | RN06        | Movimentação não pode ser editada | Histórico > abrir movimentação                          | Não há opção de editar ou excluir; correção só por ajuste com justificativa | ⏳                  |
| CT26   | RN08        | Entrada em lote existente         | Entrada com mesmo medicamento, unidade, lote e validade | Quantidade somada ao lote existente, sem criar outro                        | ⏳                  |
| CT27   | RF11        | Histórico com filtro              | Mais > Histórico > filtrar por tipo Saída               | Só aparecem saídas, da mais recente para a mais antiga                      | ⏳                  |

### 6.4 Alertas

| Código | Requisito   | Cenário                                | Passos                                                                              | Resultado esperado                                                                                         | Situação                                   |
| ------ | ----------- | -------------------------------------- | ----------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| CT28   | RF12 / RN04 | Alerta de estoque baixo                | Deixar o saldo total do medicamento na unidade ≤ mínimo > aba Alertas               | Alerta "Estoque baixo" aparece em Ativos (um por medicamento e unidade, sem linha de lote)                 | ✅ 02/11 (seed: Dipirona 1g)               |
| CT08   | RF12 / RN05 | Lote vencido ou a 7 dias               | Ter lote vencido ou a ≤ 7 dias > aba Alertas                                        | Cartão marcado como CRÍTICO, no topo da lista                                                              | ✅ 02/11 (vencidos no topo)                |
| CT49   | RF15 / RN05 | Dias de crítico configurados           | Configuração de alertas > crítico = 20 > ter lote vencendo em 15 dias > aba Alertas | O lote aparece como CRÍTICO; ao voltar para 7, perde a marca                                               | ⏳                                         |
| CT29   | RF14        | Ação recomendada                       | Abrir aba Alertas                                                                   | Cada cartão ativo mostra a ação recomendada do seu tipo                                                    | ✅ 02/11                                   |
| CT30   | RF14        | Resolver alerta                        | Marcar como resolvido > confirmar                                                   | Sai de Ativos e aparece em Resolvidos com data e hora; não volta para Ativos enquanto a situação não mudar | ✅ 02/11                                   |
| CT31   | RF14        | Ignorar alerta                         | Ignorar > confirmar                                                                 | Sai de Ativos e aparece em Ignorados                                                                       | ⏳                                         |
| CT32   | RF14        | Cancelar ação                          | Marcar como resolvido > Cancelar                                                    | Nada muda; alerta continua em Ativos                                                                       | ⏳                                         |
| CT33   | RF14        | Registro de quem resolveu              | Resolver um alerta e consultar na API                                               | Fica registrado quem resolveu e quando                                                                     | 🚫 API guarda só "quando" (pedido ao Luís) |
| CT34   | RF15        | Salvar configuração de alertas         | Mais > Configuração de alertas > alterar aviso para 45 > Salvar                     | Valor gravado na API para a unidade escolhida                                                              | ✅ 02/11                                   |
| CT35   | RF15        | Configuração inválida                  | Aviso de vencimento 456 > Salvar                                                    | Mensagem "no máximo 365 dias"; nada é gravado                                                              | ✅ 02/11                                   |
| CT36   | RF15        | Crítico maior que aviso                | Aviso 7, crítico 10 > Salvar                                                        | Mensagem "crítico precisa ter menos dias"; nada é gravado                                                  | ⏳                                         |
| CT07   | RF13        | Push de estoque baixo                  | Saída que deixa o saldo abaixo do mínimo, com o app fechado                         | Notificação chega no celular em até 5 min                                                                  | 🚫 Firebase ainda não configurado          |
| CT37   | RF13        | Toque na notificação                   | Tocar na notificação de alerta                                                      | App abre direto no alerta correspondente                                                                   | 🚫 Firebase ainda não configurado          |
| CT38   | RF13        | Permissão de notificação (Android 13+) | Primeiro acesso em Android 13 ou superior                                           | App pede permissão de notificação                                                                          | 🚫 Firebase ainda não configurado          |

### 6.5 Relatórios, offline e dashboard

| Código | Requisito | Cenário                               | Passos                                                                                      | Resultado esperado                                                                                                                           | Situação |
| ------ | --------- | ------------------------------------- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| CT39   | RF16      | Dashboard                             | Abrir Início                                                                                | KPIs batem com os dados (alertas ativos = total da aba Alertas)                                                                              | ⏳       |
| CT11   | RF17      | Exportar relatório de validade em PDF | Mais > Gerar Relatórios PDF > Validade > PDF > Compartilhar                                 | PDF gerado e aberto no menu de compartilhamento                                                                                              | ⏳       |
| CT40   | RF17      | Exportar em CSV                       | Mais > Gerar Relatórios PDF > CSV                                                           | Arquivo CSV abre em planilha                                                                                                                 | ⏳       |
| CT50   | RF18      | Estoque sem internet                  | Abrir Estoque com internet > modo avião (ou API parada) > puxar para atualizar              | Aviso "Sem conexão. Mostrando o estoque salvo em dd/MM às HH:mm" e a lista salva                                                             | ⏳       |
| CT09   | RF18      | Saída sem internet                    | Modo avião (ou API parada) > registrar saída > reconectar                                   | Mensagem "ficou salva no aparelho"; Estoque mostra "aguardando envio"; ao reconectar, a saída é enviada e aparece uma única vez no Histórico | ⏳       |
| CT41   | RN09      | Conflito na sincronização             | Offline: saída de quase todo o saldo > outra saída do mesmo lote direto na API > reconectar | Servidor rejeita; o Estoque mostra aviso vermelho com o motivo e o botão Dispensar                                                           | ⏳       |

### 6.6 Não funcionais

| Código | Requisito | Cenário                     | Resultado esperado                                                        | Situação                                  |
| ------ | --------- | --------------------------- | ------------------------------------------------------------------------- | ----------------------------------------- |
| CT42   | RNF04     | TalkBack nas telas críticas | Todos os botões e selos são lidos em voz alta com nome claro              | ⏳                                        |
| CT43   | RNF04     | Fonte ampliada do sistema   | Telas continuam legíveis, sem texto cortado                               | ⏳                                        |
| CT44   | RNF02     | Abertura a frio             | App abre em até 2,5 s num aparelho intermediário                          | ⏳                                        |
| CT45   | RNF10     | Erro de conexão             | Com a API desligada, telas mostram mensagem amigável e "Tentar novamente" | ✅ 02/11 (tela de login); demais telas ⏳ |
| CT46   | RNF14     | Datas no formato brasileiro | Todas as datas aparecem como dd/mm/aaaa                                   | ✅ 02/11 (alertas)                        |

## 7. Testes unitários automatizados (situação em 02/11/2026)

| Arquivo                  | O que cobre                                                                                                                           | Testes | Situação |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | ------ | -------- |
| `RegrasMovimentacaoTest` | RN01, RN03, RN06, FEFO, datas                                                                                                         | 8      | ✅       |
| `AgruparEstoqueTest`     | Agrupamento e situação do estoque                                                                                                     | 6      | ✅       |
| `ErrosTest`              | Mensagens de erro da API                                                                                                              | 4      | ✅       |
| `FormatosTest`           | Datas dd/mm/aaaa                                                                                                                      | 4      | ✅       |
| `RegrasAlertaTest`       | Gravidade, crítico (inclusive com os dias configurados por unidade), ação recomendada, ordem, abas                                    | 12     | ✅       |
| `RegrasConfiguracaoTest` | Validação da configuração de alertas                                                                                                  | 8      | ✅       |
| `ConversoesOfflineTest`  | Estoque salvo no aparelho, lote vencido depois de salvo (RN03 offline), saldo após movimentação offline (RN01), item de sincronização | 6      | ✅       |

**Lacunas:** RN04 e RN09 são verificadas na API (pytest do backend, incluindo `test_sync.py` para reenvio sem duplicação). No app, RN02 (FEFO) e RN05 (dias de crítico) já têm teste; RN07 e RN08 dependem do servidor e são cobertas por CT16 e CT26.

## 8. Matriz de rastreabilidade (requisito > caso de teste)

| Requisito          | Casos                        |
| ------------------ | ---------------------------- |
| RF01               | CT01, CT02, CT13, CT47, CT48 |
| RF02               | CT03                         |
| RF03               | CT14, CT15                   |
| RF04               | CT10                         |
| RF05 / RF06 / RF07 | CT17, CT18, CT19, CT20       |
| RF08               | CT21                         |
| RF09               | CT04, CT22                   |
| RF10               | CT05, CT06                   |
| RF11               | CT27                         |
| RF12               | CT28, CT08                   |
| RF13               | CT07, CT37, CT38             |
| RF14               | CT29, CT30, CT31, CT32, CT33 |
| RF15               | CT34, CT35, CT36, CT49       |
| RF16               | CT39                         |
| RF17               | CT11, CT40                   |
| RF18               | CT50, CT09, CT41             |
| RN01               | CT06                         |
| RN02               | CT23                         |
| RN03               | CT24                         |
| RN04               | CT28                         |
| RN05               | CT08, CT49                   |
| RN06               | CT25                         |
| RN07               | CT10, CT16                   |
| RN08               | CT26                         |
| RN09               | CT41                         |
