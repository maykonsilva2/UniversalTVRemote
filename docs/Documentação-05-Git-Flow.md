# Manual de Engenharia: Operações e Arquitetura Git Flow

Este documento estabelece as regras de controle de versão (Git) para o projeto UniversalTVRemote. Se você é novo no projeto ou no uso do Git, não se preocupe! Este guia foi feito para ser seguido passo a passo. 

O objetivo principal aqui é garantir que o código que vai para os usuários (produção) esteja sempre funcionando, enquanto você e outros desenvolvedores podem criar novas funcionalidades sem atrapalhar o trabalho um do outro.

---

## 1. Topologia de Ramificações (Branches)

Uma "ramificação" ou "branch" é como uma cópia do projeto onde você pode trabalhar com segurança. Nosso repositório é dividido entre branches que existem para sempre e branches temporárias.

### 1.1 Ramificações Perenes (Existem sempre)
* **`main`**: Representa o aplicativo real que os usuários baixam. O código aqui deve estar **sempre funcionando perfeitamente**. Você **nunca** deve salvar (comitar) código diretamente aqui.
* **`develop`**: É o nosso ambiente de testes e integração. Todo código novo que funciona bem vem parar aqui antes de ir para a `main`.

### 1.2 Ramificações Efêmeras (Temporárias)
* **`feature/*`**: É aqui que você vai trabalhar a maior parte do tempo! Sempre que for criar algo novo (uma tela, um botão, uma funcionalidade), você cria uma branch `feature/nome-da-sua-tarefa` a partir da `develop`.
* **`hotfix/*`**: Só usada quando a casa está caindo! Se descobrirmos um erro (bug) grave lá na `main` (em produção), criamos uma `hotfix` para arrumar rapidinho.

---

## 2. Ciclo de Vida de uma Funcionalidade (Fluxo Passo a Passo)

Sempre que você for pegar uma nova tarefa no projeto, siga estritamente estas etapas:

### Passo 1: Isolamento do Ambiente (Criação da sua Feature)
Antes de começar a programar, você precisa garantir que está na base correta e criar o seu espaço de trabalho seguro.

```bash
# 1. Vá para a branch principal de desenvolvimento
git checkout develop

# 2. Atualize o seu computador com o código mais recente que está na nuvem (GitHub/GitLab)
git pull origin develop

# 3. Crie a sua própria branch (sua cópia isolada) e entre nela imediatamente
# O "-b" significa "criar branch". Use sempre tudo minúsculo e com hifens!
git checkout -b feature/nome-da-tarefa
# Exemplo prático: git checkout -b feature/setup-hilt
```

> **💡 Dica de Iniciante:** Neste momento, você está seguro. Qualquer código que você estragar ou deletar só vai afetar a sua branch!

### Passo 2: Registro Atômico (Salvando seu trabalho com Commits)
Ao longo do dia, conforme for terminando pequenas partes da tarefa, você deve "salvar" (comitar) seu progresso. Evite deixar para salvar tudo só no final do mês! Sempre englobe apenas os arquivos pertinentes àquela modificação. Utiliza-se a convenção "Conventional Commits" para rastreabilidade.

```bash
# 1. Adiciona todos os arquivos modificados na área de preparação (Staging Area)
git add .

# 2. Registra as alterações no histórico com um prefixo semântico explicando o que você fez
git commit -m "feat: implementar injeção de dependência com Hilt na classe Application"
```

**Prefixos Semânticos Obrigatórios (Como começar a sua mensagem do commit):**
* **`feat:`** Para novas funcionalidades do aplicativo (do inglês *feature*).
* **`fix:`** Para correção de falhas e anomalias de execução (bugs).
* **`chore:`** Para atualização de dependências, configurações de build ou Gradle (coisas invisíveis ao usuário).
* **`docs:`** Para alterações exclusivas em arquivos Markdown ou documentação técnica (como este arquivo).
* **`refactor:`** Para reestruturação de código existente sem alteração de comportamento.

### Passo 3: Integração e Fusão (Enviando finalizado - Merge)
Terminou sua tarefa? O código já foi testado e compilou sem erros (`./gradlew assembleDebug`)? Então é hora de juntar seu código com o restante do time na `develop`.

```bash
# 1. Volte para o contexto da branch de integração
git checkout develop

# (Opcional, porém recomendado): Puxe as coisas novas da internet na "develop" com git pull origin develop

# 2. Funde (junte) as alterações da sua feature na ramificação develop
git merge feature/nome-da-tarefa

# 3. Exclui a ramificação local efêmera do seu computador para manter o repositório conciso
git branch -d feature/nome-da-tarefa
```

---

## 3. Comandos Salva-Vidas (Manutenção e Resolução de Anomalias)

Durante a operação do sistema de versionamento, a invocação dos seguintes comandos vai te ajudar muito no dia a dia, tanto na auditoria quanto reversão de estados indesejados.

### 3.1 Onde eu estou e o que eu fiz? (Auditoria de Estado)
```bash
# Seu melhor amigo! Exibe o estado atual da árvore de trabalho e arquivos não rastreados.
git status

# Exibe o registro histórico de modificações e hashes de commit no terminal de forma visual.
git log --oneline --graph --decorate
```

### 3.2 Socorro, fiz besteira e quero limpar tudo! (Reversão de Alterações não Registradas)
Se você bagunçou o código e quer que os arquivos voltem a ser como estavam antes de você mexer (e ainda não fez o `git commit` - arquivos Uncommitted):

```bash
# Descarta todas as modificações no diretório de trabalho que ainda não foram comitadas
git restore .

# Remove arquivos novos não rastreados (Untracked) do diretório de trabalho
git clean -fd
```
> **⚠️ Cuidado:** Esses comandos apagam seu trabalho não salvo. Não tem volta depois deles!

### 3.3 Guardando no Rascunho Temporário (Stash)
Caso seja necessário alterar o contexto (trocar de ramificação) sem registrar um commit incompleto (por exemplo, pedem para você analisar um erro rápido na `main`):

```bash
# Salva as alterações bagunçadas na memória temporária do Git (uma "gaveta invisível")
git stash

# ...vá para onde precisar ir, ajude, e então volte para a sua ramificação...

# Restaura as alterações arquivadas (abre a "gaveta") de volta na sua área de trabalho
git stash pop
```



⚠️ **Atenção como fazer hotfix**

A operação de hotfix constitui um procedimento arquitetural de emergência no modelo Git Flow. Ela é invocada exclusivamente quando uma falha crítica é identificada no ambiente de produção (a ramificação main) e exige resolução imediata, ignorando o ciclo de desenvolvimento padrão da ramificação develop.

Abaixo, detalha-se o protocolo técnico e a sequência exata de comandos para instanciar, executar e finalizar uma ramificação de hotfix para corrigir um artefato (como o README.md).

Protocolo de Execução de Hotfix
1. Instanciação a partir da Produção
O primeiro passo exige a alteração do ponteiro HEAD do Git para a ramificação main, garantindo que a base da correção seja o código exato que está público no repositório.

```bash
# Alterna para a ramificação de produção
git checkout main

# Sincroniza o estado local com o servidor remoto para garantir a paridade
git pull origin main

# Cria a ramificação de emergência e altera o contexto de trabalho
git checkout -b hotfix/correcao-readme

#2. Execução e Registro (Commit) Neste estado isolado, você realiza a modificação necessária no arquivo README.md através da sua IDE ou editor de texto. Após a correção, o artefato deve ser registrado no histórico.
```

```bash
# Adiciona o arquivo corrigido à área de preparação (Staging Area)
git add README.md

# Registra a alteração com a semântica apropriada
git commit -m "fix: corrigir erro tipografico critico na documentacao principal"

#3. Fusão e Promoção (Merge para main) Com a anomalia resolvida no escopo do hotfix, a correção deve ser promovida de volta para o ambiente de produção.
```

```bash
# Retorna para a ramificação principal
git checkout main

# Funde a correção no histórico da produção
git merge hotfix/correcao-readme

# Envia o estado atualizado para o servidor GitHub
git push origin main

#4. Sincronização Reversa (Backporting para develop) Este é o passo arquitetural mais crítico do Git Flow. A correção aplicada na main deve ser obrigatoriamente replicada na ramificação develop. Caso esta etapa seja omitida, o erro retornará à produção no próximo lançamento (Release), gerando uma regressão de software.
```

```bash
# Alterna para a ramificação de integração contínua
git checkout develop

# Sincroniza o ambiente de desenvolvimento com a correção aplicada
git merge hotfix/correcao-readme

# Envia a atualização para o servidor
git push origin develop

#5. Exclusão da Ramificação Efêmera Após garantir a integridade estrutural em ambas as ramificações perenes (main e develop), o escopo temporário do hotfix deve ser descartado.
```

```bash
# Remove a ramificação de emergência do sistema de arquivos local
git branch -d hotfix/correcao-readme
```

Este fluxo garante a manutenção da estabilidade do código e a auditoria rigorosa do histórico de versões.