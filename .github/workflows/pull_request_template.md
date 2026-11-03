## O que muda

<!-- Descreva em poucas linhas o que este PR faz e por quê. -->

## Tipo de mudança

- [ ] Nova funcionalidade
- [ ] Correção de defeito
- [ ] Ajuste visual ou de texto
- [ ] Refatoração (sem mudar o comportamento)
- [ ] Documentação
- [ ] Backend (API)

## Parte do projeto

- [ ] App Android (`RemedioCerto/`)
- [ ] API (`backend/`)
- [ ] Documentação (`README.md`, `docs/`)

## Requisitos e casos de teste relacionados

<!-- Ex.: RF10, RN01, CT05. Se corrigir uma issue, escreva "Closes #número". -->

## Como testar

<!-- Passo a passo para quem for revisar. Ex.:
1. Subir a API (backend/README.md) e rodar `adb reverse tcp:8000 tcp:8000`
2. Entrar como administrador
3. Escanear > registrar uma saída
4. Conferir o saldo no Estoque -->

1.
2.
3.

## Evidências

<!-- Prints ou vídeo curto da tela, quando houver mudança visual. -->

## Checklist

- [ ] O app compila (Build > Rebuild Project) e abre sem erro
- [ ] Testes do app passando (`.\gradlew test` em `RemedioCerto/`)
- [ ] Testes da API passando (`python -m pytest -q` em `backend/`), se mexeu no backend
- [ ] Testei no emulador ou no celular
- [ ] Testei como administrador e como funcionário, se a mudança depende do perfil
- [ ] Sem senhas, tokens, `.env` ou arquivos de teste no commit
- [ ] Branch atualizada com a `main` antes de pedir revisão
