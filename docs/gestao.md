# Gestão do Remédio Certo

Responsável: Luís Gustavo Rocha Gomes de Andrade. Referência de acompanhamento: 24/10/2026. As semanas S1 a S6 são relativas ao início do projeto; a equipe deve registrar as datas acordadas no planejamento. Nenhuma reunião ou homologação é presumida como realizada.

## Quadro de tarefas

Fluxo: **Backlog → A fazer → Em andamento → Em revisão → Testes → Concluído**. Este quadro versionado pode ser acompanhado no próprio repositório e transposto para GitHub Projects. Um cartão só avança com a evidência do respectivo critério de aceite.

| ID | Entrega | Responsável | Estado de acompanhamento | Critério de aceite / evidência |
| --- | --- | --- | --- | --- |
| LG01 | API compatível com login, cadastros, estoque, movimentações e dashboard atuais | Luís Gustavo | Em revisão da implementação | Contratos do app preservados; testes de autenticação, autorização e estoque aprovados; PR revisado. |
| LG02 | Access token de 15 min e refresh de 7 dias | Luís Gustavo | Em revisão da implementação | Renovação com rotação e limite absoluto de sete dias; replay revoga a família; logout revoga a sessão; troca de senha preserva a corrente e revoga as demais; testes aprovados. |
| LG03 | Recuperação de senha por e-mail | Luís Gustavo | Em revisão; homologação externa pendente | Código/link de uso único entregue no e-mail de teste; expiração, limite de tentativas e revogação de sessões verificados; conta inexistente sem exposição. |
| LG04 | Registro do aparelho e push quando gerar alerta | Luís Gustavo | Em revisão; homologação externa pendente | Token FCM vinculado ao usuário; envio restrito à unidade; payload identifica o alerta; falha temporária admite nova tentativa; teste em aparelho com Flávia. |
| LG05 | Sincronização de movimentações em lote | Luís Gustavo | Em revisão da implementação | Reenvio idempotente; saldo revalidado no servidor; conflito informado por operação; acesso entre unidades bloqueado; teste com fila de Paulo. |
| LG06 | API HTTPS e PostgreSQL em homologação | Luís Gustavo | A fazer: acesso ao provedor pendente | URL HTTPS real com `/health` saudável; banco persistente; backup definido; smoke test do app; serviço de e-mail e push ativos. |
| LG07 | Quadro, rituais, marcos e riscos | Luís Gustavo | Em revisão deste documento | Equipe confirma responsáveis e datas; cartão tem aceite e evidência; atas e riscos são atualizados nos acompanhamentos. |
| IN01 | Consumir refresh e recuperação de senha no Android | Silmara, com Luís Gustavo no contrato | A fazer: integração | Sessão renovada e recuperação acessível no app; logout mantém o comportamento esperado. |
| IN02 | Cache, fila e sincronização automática Android | Paulo Henrique | A fazer: integração | Operações persistem sem rede; reconexão sincroniza uma vez; conflitos visíveis ao usuário. |
| IN03 | Firebase Android, notificações e telas de alerta | Flávia | A fazer: integração | Registro/renovação do token FCM; toque abre alerta após autenticação; recebimento em aparelho físico. |
| IN04 | Revisão visual e documentação do usuário | Luiz Carlos | A fazer: conforme escopo da equipe | Layout e textos revisados; manual corresponde à versão entregue. |

Os estados de implementação acima indicam preparação para revisão do código desta entrega, sem substituir o aceite e os testes de homologação. As funções Android já prontas informadas pela equipe são preservadas. Scanner, nova movimentação e histórico não são tarefas de reconstrução.

### Cartão de trabalho

Ao abrir uma tarefa, registrar: ID, descrição, responsável, prioridade MoSCoW, marco, dependências, estimativa, prazo acordado e critério de aceite. Durante o trabalho, acrescentar branch, PR, evidência de teste, impedimento, próximo passo e data da última atualização. Toda mudança de prazo deve registrar motivo, impacto e novo acordo.

## Marcos e prazos

| Marco | Prazo relativo | Evidência necessária | Acompanhamento |
| --- | --- | --- | --- |
| M1 | Fim da S1 | Plano e backlog aprovados pela equipe/professor | Registrar aprovação e data real. |
| M2 | Fim da S2 | Protótipo validado, arquitetura e CI funcionando | Registrar links e execução de CI. |
| M3 | Fim da S4 | API HTTPS publicada; núcleo Android integrado e demonstrado | LG01–LG06, URL e roteiro de smoke test. |
| M4 | Fim da S5 | Beta com Must e Should, sem defeito crítico | Quadro atualizado e relatório de testes. |
| M5 | Metade da S6 | Homologação em aparelhos e SUS ≥ 75 | Flávia e Luiz Carlos registram resultados reais. |
| M6 | Fim da S6 | APK assinado, código e documentação entregues | Checklist de entrega e aceite. |

Luís Gustavo revisa em cada acompanhamento os cartões do marco atual, o trabalho restante e as dependências. Qualquer bloqueio que ameace o marco entra na matriz de riscos com responsável e próxima ação. Datas de calendário e convites devem ser acordados com a equipe; não estão agendados por este documento.

## Reuniões e registro de decisões

Rotina proposta a partir do plano:

- Segunda-feira: planejamento da sprint semanal, definindo objetivo, cartões e capacidade.
- Três acompanhamentos on-line por semana, de 15 minutos: avanço, próximo passo e impedimento de cada responsável.
- Sexta-feira: revisão com demonstração e evidências; em seguida, retrospectiva com uma melhoria prática para a semana seguinte.

Luís Gustavo mantém a ata junto ao cartão ou em documento versionado. Uma reunião ocorre somente quando os participantes a realizam; este modelo não registra presença ou decisão antecipadamente.

```text
Data e horário:
Tipo de reunião / sprint:
Participantes presentes:
Objetivo:
Demonstrações e evidências (PR / teste / aparelho):
Decisões tomadas:
Ações: responsável | ação | prazo acordado | cartão
Impedimentos e riscos alterados:
Próximo encontro acordado:
```

## Riscos e dependências

| ID | Risco / sinal de atenção | Probabilidade / impacto | Responsável | Resposta e próxima verificação |
| --- | --- | --- | --- | --- |
| R01 | Conta de hospedagem, e-mail ou Firebase não configurada | Alta / alto | Luís Gustavo | Obter acesso ao projeto correto; configurar segredos no provedor; executar testes externos LG03, LG04 e LG06. Sem isso, não declarar hospedagem/push/e-mail homologados. |
| R02 | Prazo de seis semanas e dependências entre API e Android | Alta / alto | Luís Gustavo | Planejar contratos primeiro; limitar trabalho em andamento; conferir M3/M4 a cada reunião. Priorizar Must antes de novos recursos. |
| R03 | Reenvio offline duplicado ou saldo disputado entre aparelhos | Média / alto | Luís Gustavo + Paulo | Usar identificador local estável, idempotência e transação no servidor; testar reenvio e disputa de saldo no PostgreSQL. |
| R04 | Push ou scanner sem validação em aparelho | Média / médio | Flávia + Paulo | Reservar aparelho físico; testar permissão, câmera, token, recebimento e abertura do alerta. |
| R05 | Serviço gratuito suspenso por inatividade, reinício ou limite do plano | Média / médio | Luís Gustavo | Verificar limites antes da demonstração; manter Docker portátil e exportação do banco; não depender de SQLite efêmero em nuvem. |
| R06 | E-mail SMTP bloqueado pelo plano gratuito do Render | Alta no plano gratuito / alto | Luís Gustavo | Usar serviço de e-mail com API HTTPS ou hospedagem/plano com SMTP permitido; validar envio no ambiente publicado antes de LG03 ser concluído. |
| R07 | Banco gratuito do Render expira antes da entrega | Média / alto | Luís Gustavo | Registrar criação/expiração real do banco; exportar dados fictícios; escolher alternativa de persistência com a equipe antes do vencimento. |
| R08 | Agendador deixa de executar com serviço gratuito adormecido | Alta no plano gratuito / alto | Luís Gustavo | Testar periodicidade em homologação; acordar serviço sempre ativo ou execução externa antes de prometer prazo de entrega de alertas. |
| R09 | Vazamento de credenciais em Git ou logs | Média / alto | Luís Gustavo | Segredos apenas em variáveis/secret files do provedor; revisar diff antes de push; não registrar senha, JWT ou token FCM em logs. |
| R10 | Testes ou revisões incompletos ocultam incompatibilidade | Média / alto | Equipe | Exigir revisão de colega e CI; validar contratos existentes e smoke test do APK contra HTTPS. |

Revisão dos riscos: nos acompanhamentos e na sexta-feira, registrar a data, probabilidade/impacto revistos, medida executada e resultado. O dono do risco solicita ajuda quando houver impacto no marco da sprint.

Os limites citados para Render Free constam na [documentação oficial](https://render.com/docs/free): suspensão por inatividade, expiração de PostgreSQL gratuito em 30 dias e bloqueio SMTP nas portas usuais. A configuração gratuita serve à homologação e precisa dessas verificações.

## Integração e entrega

Pendências externas de Luís Gustavo: selecionar conta/projeto de hospedagem, cadastrar as credenciais de e-mail e Firebase no provedor, criar o administrador inicial com senha própria, publicar HTTPS e registrar URL e evidências. Nenhuma senha ou chave deve ir para o GitHub.

Pendências coordenadas com outros integrantes: Silmara integra os novos endpoints de sessão e recuperação; Paulo mantém o identificador de cada operação durante os reenvios; Flávia registra/atualiza o token do aparelho, implementa o destino do push e valida em aparelho. Os contratos da API devem ser a referência compartilhada.

Antes do merge: revisão de ao menos um colega, CI aprovado, contratos documentados e critérios de aceite atendidos. Antes do aceite da entrega: testar o APK em aparelhos, confirmar URL HTTPS, envio real de e-mail e push, sincronização offline, ausência de defeitos críticos e documentação correspondente. Commits seguem em branch de tarefa e PR; a branch `main` não recebe commits diretos.
