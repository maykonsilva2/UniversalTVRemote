# 📱 Universal TV Remote App — Documentação Completa e Corrigida

> **Versão**: 4.0 — Documentação Consolidada e Corrigida  
> **Sistema**: CachyOS (Arch Linux)  
> **Dispositivos de teste**: Galaxy S21 FE + POCO Pad  
> **Linguagem**: Kotlin + Jetpack Compose  
> **Autor**: Documentação gerada com base nas Documentações 01, 02 e 03

---

## 📋 Índice Geral das Etapas

Esta documentação está dividida em 5 **Etapas de Desenvolvimento** lógicas, projetadas para levar o projeto do zero até a publicação.

### **Etapa 1: Setup e Infraestrutura** (Neste Arquivo)
1. Pré-requisitos e Instalação do Ambiente
2. Criação e Configuração do Projeto
3. Estrutura de Pastas do Projeto
4. Configuração do Git e Git Flow
5. Gradle: Dependências e Configurações
6. Permissões no AndroidManifest.xml

### **Etapa 2: Arquitetura e Comunicação de TVs** (Parte 2)
7. Arquitetura MVVM + Clean Architecture
8. Módulo Core — Modelos e Contratos
9. Descoberta de Dispositivos na Rede (mDNS)
10. Emparelhamento e Conexão por Marca (WebSockets/HTTP)

### **Etapa 3: Interface Glassmorphism & UI** (Parte 4)
- *Esta etapa foi movida para a Parte 4, traduzindo o Protótipo HTML/CSS em Jetpack Compose.*

### **Etapa 4: Banco de Dados e App Completo** (Parte 3)
11. Persistência de Dados com Room
12. (Veja Parte 4 para a UI principal)
13. ViewModel e Camada de Apresentação
14. Injeção de Dependência com Hilt

### **Etapa 5: Qualidade, DevOps e Publicação** (Parte 3)
15. Testes Unitários e de Integração
16. Docker para Build e Simulador de TV
17. CI/CD com GitHub Actions
18. Build, Assinatura e Publicação
19. Wireless Debugging no CachyOS
20. Checklist de Implementação
21. Recursos e Links Oficiais

---

# 🛠️ ETAPA 1: Setup e Infraestrutura
Nesta primeira etapa, prepararemos o terreno: instalaremos o Android Studio no Linux, criaremos a estrutura básica de pastas, configuraremos as versões do Gradle e liberaremos o acesso à Internet/Rede para que o app consiga encontrar as TVs.

## 1. Pré-requisitos e Instalação do Ambiente

### 1.1 Instalar JDK 17 no CachyOS [✅]

O Android moderno exige o JDK 17. No CachyOS (base Arch Linux):

```bash
# Instalar JDK 17
sudo pacman -S jdk17-openjdk

# Verificar instalação
java -version
# Saída esperada: openjdk version "17.x.x"

# Se tiver múltiplos JDKs instalados, selecionar o 17
sudo archlinux-java set java-17-openjdk

# Confirmar JDK ativo
archlinux-java status
```

### 1.2 Instalar Android Studio no CachyOS [✅]

**Opção A — Via AUR (recomendado):** [✅]
```bash
# Instalar yay se ainda não tiver
sudo pacman -S yay

# Instalar Android Studio
yay -S android-studio
```

**Opção B — Via Flatpak:**
```bash
# Instalar Flatpak se ainda não tiver
sudo pacman -S flatpak

# Adicionar repositório Flathub
flatpak remote-add --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo

# Instalar Android Studio
flatpak install flathub com.google.AndroidStudio

# Executar
flatpak run com.google.AndroidStudio
```

**Opção C — JetBrains Toolbox (mais fácil de atualizar):**
```bash
# Baixar Toolbox App do site oficial:
# https://www.jetbrains.com/toolbox-app/
# Extrair e executar:
chmod +x jetbrains-toolbox-*.AppImage
./jetbrains-toolbox-*.AppImage
# Dentro do Toolbox, instalar Android Studio
```

    ### 1.3 Configurar Android SDK

Após instalar o Android Studio:

1. Abra o Android Studio → **SDK Manager** (ícone de engrenagem ou menu Tools)
2. Na aba **SDK Platforms**, instale:
   - ✅ **Android 15.0 (API 35)** — targetSdk atual (alvo principal, Google Play exige API 35+ a partir de ago/2025) [✅]
   - ✅ **Android 16.0 (API 36)** — para suporte futuro (compileSdk) [✅]
   - ✅ **Android 8.0 (API 26)** — minSdk (suporte mínimo) [✅]
3. Na aba **SDK Tools**, instale:
   - ✅ Android SDK Build-Tools 36.0.0  [✅]
   - ✅ Android SDK Platform-Tools [✅]
   - ✅ Android Emulator [✅]
   - ✅ Android SDK Command-line Tools (latest) [✅]

> 📌 **Estratégia de versões:**
> - `minSdk = 26` → suporta Android 8.0+ (cobre ~99% dos dispositivos ativos)
> - `targetSdk = 35` → alvo Android 15 (exigido pela Play Store a partir de ago/2025)
> - `compileSdk = 36` → compila contra Android 16, aproveitando as APIs mais novas
>   sem obrigar comportamentos do Android 16 em runtime (que só ocorrem com `targetSdk = 36`)

### 1.4 Instalar ADB e ferramentas Android no sistema [✅]

```bash
# Instalar android-tools (inclui adb, fastboot)
sudo pacman -S android-tools

# Verificar ADB
adb version
# Saída esperada: Android Debug Bridge version 1.x.x

# Adicionar usuário ao grupo adbusers (para USB sem sudo)
sudo usermod -aG adbusers $USER

# Reiniciar sessão ou aplicar grupo imediatamente
newgrp adbusers
```

### 1.5 Instalar Git e Docker [✅]

```bash
# Git
sudo pacman -S git

# Configurar identidade Git (obrigatório para commits)
git config --global user.name "Seu Nome"
git config --global user.email "seu@email.com"

# Docker
sudo pacman -S docker docker-compose

# Habilitar e iniciar o serviço Docker
sudo systemctl enable docker
sudo systemctl start docker

# Adicionar usuário ao grupo docker (sem sudo)
sudo usermod -aG docker $USER
newgrp docker

# Verificar
docker --version
docker compose version
```

---

## 2. Criação e Configuração do Projeto

### 2.1 Criar Novo Projeto no Android Studio [✅]

1. Abra o **Android Studio**
2. Clique em **New Project**
3. Selecione o template **Empty Activity** (com Jetpack Compose)
4. Configure os campos:

| Campo | Valor |
|-------|-------|
| **Name** | UniversalTVRemote |
| **Package name** | com.antoniosilva.universaltvremote |
| **Save location** | ~/Projetos/UniversalTVRemote |
| **Language** | Kotlin |
| **Minimum SDK** | API 26 (Android 8.0) |
| **Build configuration language** | Kotlin DSL (build.gradle.kts) |

5. Clique em **Finish** e aguarde o Gradle sincronizar

> ⚠️ **Atenção**: Use **API 26** como minSdk (não API 24 como citado na Doc-02). O motivo: recursos de rede modernos, WebSockets robustos e APIs de descoberta de dispositivos estão melhor suportadas a partir do Android 8.0. Isso ainda cobre mais de 98% dos dispositivos ativos.

### 2.2 Verificar que o projeto compila [✅]

```bash
cd ~/Projetos/UniversalTVRemote

# Dar permissão de execução ao Gradle Wrapper
chmod +x gradlew

# Fazer build inicial de verificação
./gradlew assembleDebug

# Saída esperada: BUILD SUCCESSFUL
```

---

## 3. Estrutura de Pastas do Projeto

### ⚠️ **Atenção**: A Seção 3 ilustra como a estrutura final do projeto deve se parecer após a conclusão de todas as etapas

Após criar o projeto, sua estrutura final deve se parecer com:

```
UniversalTVRemote/
├── .github/
│   └── workflows/
│       ├── android-ci.yml          # CI: lint, testes, build
│       └── release.yml             # CD: build de release com tag
├── docker/
│   ├── Dockerfile                  # Ambiente de build Android
│   ├── docker-compose.yml          # Orquestração de serviços
│   └── tv-simulator/
│       ├── Dockerfile              # Simulador de TV
│       └── simulator.py            # Servidor Python que simula TV
├── scripts/
│   └── deploy.sh                   # Script de deploy automatizado
├── docs/
│   └── architecture.md
├── app/                            # Módulo principal Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/antoniosilva/universaltvremote/
│   │   │   │   ├── data/           # Repositórios e fontes de dados
│   │   │   │   │   ├── local/      # Room (banco local)
│   │   │   │   │   └── remote/     # WebSocket, HTTP
│   │   │   │   ├── domain/         # Modelos e interfaces (contratos)
│   │   │   │   │   ├── model/
│   │   │   │   │   └── repository/
│   │   │   │   ├── presentation/   # UI e ViewModels
│   │   │   │   │   ├── screens/
│   │   │   │   │   ├── components/
│   │   │   │   │   └── viewmodel/
│   │   │   │   └── di/             # Módulos Hilt
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                   # Testes unitários (JVM)
│   │   └── androidTest/            # Testes instrumentados (dispositivo)
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml          # Catálogo centralizado de versões
│   └── wrapper/
│       └── gradle-wrapper.properties
├── .gitignore
├── settings.gradle.kts
└── build.gradle.kts                # Raiz do projeto
```

---

## 4. Configuração do Git e Git Flow

### 4.1 Inicializar repositório e criar .gitignore [✅]

```bash
cd ~/Projetos/UniversalTVRemote

# Iniciar repositório Git (se ainda não foi iniciado)
git init

# Criar .gitignore completo para Android
cat > .gitignore << 'EOF'
# Gradle
.gradle/
build/
*/build/
!gradle-wrapper.jar
!gradle-wrapper.properties

# Android Studio / IntelliJ
.idea/
*.iml
*.ipr
*.iws
local.properties
captures/
.externalNativeBuild/
.cxx/

# Kotlin
*.class
*.kotlin_module

# Sistema Operacional
.DS_Store
Thumbs.db

# Segredos (NUNCA commitar!)
secrets.properties
keystore.properties
*.jks
*.keystore
google-services.json
firebase-app-distribution.json

# Docker
.dockerignore
EOF

# Primeiro commit [✅]
git add .
git commit -m "chore: initial commit with project structure and gitignore"
```

### 4.2 Configurar Git Flow (estratégia de branches) [✅]

```bash
# Criar branch de desenvolvimento
git checkout -b develop

# Estrutura de branches:

# main     — código de produção (sempre estável) É a ramificação primária de distribuição. O código contido na main deve estar estritamente em estado funcional e compilável.

# develop  — integração de features o seu conteúdo é fundido (merged) com a main para gerar uma nova versão (Release).

# feature/nome-da-feature — novas funcionalidades. São ramificações efêmeras (temporárias) criadas sempre a partir da develop. Elas servem para encapsular o código de uma nova funcionalidade específica. Isso impede que um código incompleto quebre o ambiente de integração.

# hotfix/nome-do-bug      — correções urgentes em produção. São ramificações de emergência. Diferente das features, um hotfix é ramificado diretamente a partir da main. Seu propósito é corrigir uma falha crítica em produção (ex: um crash imediato ao abrir o app). Após a correção, o código é fundido simultaneamente na main (para gerar a atualização do app) e na develop (para garantir que o bug não retorne nas próximas versões).
```

### 4.3 Criar repositório remoto no GitHub

```bash
# Instalar GitHub CLI (opcional, mas facilita)
sudo pacman -S github-cli [✅]

# Autenticar
gh auth login [✅]

# Criar repositório remoto (Público para portfólio)
gh repo create UniversalTVRemote --public --source=. --push  [✅] 

# Ou manualmente: criar no GitHub.com e adicionar remote
git remote add origin https://github.com/antoniosilva/UniversalTVRemote.git
git push -u origin main
git push -u origin develop
```

### 4.4 Licenciamento de Software (Licença MIT) [✅]

Para manter o código aberto (*Open Source*) e, simultaneamente, reter o direito de comercialização ou publicação autônoma na Google Play Store, a adoção de uma licença permissiva é mandatória. A **Licença MIT** é o padrão da indústria para este cenário, pois isenta o autor de responsabilidade civil sobre o uso de terceiros, mas permite a livre distribuição do código.

Execute o seguinte comando no terminal (na raiz do projeto) para gerar o arquivo `LICENSE`:

```bash
cat > LICENSE << 'EOF'
MIT License

Copyright (c) 2026 Antonio Silva

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
EOF
```

### 🔁 **O Ciclo do Git Flow (Quando Fazer Commits?)** [✅]

Sempre que você finalizar uma etapa lógica (como criar um arquivo importante, finalizar a construção de uma tela ou configurar o banco de dados), você deve registrar (commitar) essas mudanças no Git e enviá-las para o repositório remoto GitHub. Isso garante que seu trabalho  está documentado, versionado e seguro.

**Como você acabou de criar o arquivo `LICENSE`**, este é o momento perfeito para rodar o ciclo do Git:

```bash
# 1. Prepara todos os arquivos adicionados ou modificados (staging)
git add LICENSE

# 2. Registra o lote de mudanças com uma mensagem semântica (o que foi feito)
git commit -m "docs: adicionar licença MIT para o projeto open source"

# 3. Envia o histórico local com segurança para o servidor (na branch develop)
git push origin develop
```

> 💡 **Regra de Ouro (Repetição):** Você fará essa sequência de 3 comandos dezenas de vezes durante o desenvolvimento. Por exemplo, logo após criar o `README.md` no próximo passo, você rodará `git add README.md`, fará um `commit` descrevendo a criação do README, e um `push` para enviar.


### 4.5 Documentação de Apresentação (README.md) [✅]

O arquivo `README.md` localizado na raiz do projeto é a interface de entrada do seu repositório no GitHub. Para fins curriculares, ele deve expor de forma explícita a pilha tecnológica (*Tech Stack*) e os padrões de projeto (*Design Patterns*) que você domina. Em processos de triagem técnica, engenheiros de *software* e recrutadores avaliam a legibilidade, a arquitetura e o licenciamento antes de executar o código.

Execute o comando abaixo para gerar um `README.md` estruturado com as especificações da sua arquitetura:

```bash
cat > README.md << 'EOF'
# Universal TV Remote 📺

Um aplicativo Android nativo, de código aberto, desenvolvido para atuar como um controle remoto universal para Smart TVs via rede Wi-Fi (WebSockets e APIs REST).

> **Aviso de Transparência Curricular:** A documentação e base arquitetural deste projeto foram elaboradas com o auxílio de ferramentas de Inteligência Artificial para fins de aceleração de aprendizado. No entanto, é fundamental destacar que **eu estudei, escrevi e revisei** rigorosamente cada trecho de lógica, arquitetura e documentação para extrair a máxima compreensão sobre o ecossistema Android moderno. Esta aplicação não é apenas código gerado, mas o reflexo do meu entendimento técnico aplicado na prática.

Este projeto tem como objetivo demonstrar a implementação de padrões rigorosos de engenharia de software moderno no ecossistema Android, visando escalabilidade, testabilidade e manutenibilidade.

## 🏗 Arquitetura de Software
O aplicativo foi estruturado utilizando **Clean Architecture** em conjunto com o padrão de apresentação **MVVM (Model-View-ViewModel)**. A segregação de responsabilidades é dividida nas seguintes camadas:
* **Presentation:** Interface de usuário reativa construída 100% com Jetpack Compose.
* **Domain:** Regras de negócio encapsuladas, abstrações de repositórios e casos de uso (Use Cases).
* **Data:** Implementação de repositórios, comunicação de rede (Retrofit/OkHttp) e persistência local (Room).

## 🛠 Tech Stack e Bibliotecas
* **Linguagem:** Kotlin 2.1.0
* **Build System:** Gradle (Kotlin DSL / Version Catalogs `libs.versions.toml`)
* **UI:** Jetpack Compose (BOM 2025.05.01) com suporte a Edge-to-Edge
* **Injeção de Dependência:** Dagger Hilt
* **Assincronismo e Concorrência:** Kotlin Coroutines & Flows
* **Rede:** Retrofit, OkHttp e WebSockets
* **Persistência de Dados:** Room Database
* **Testes:** JUnit 4, MockK, Turbine, Espresso

## 🚀 Compilação e Execução
O projeto utiliza o Gradle Wrapper. Para compilar a variante de depuração localmente:
\`\`\`bash
./gradlew assembleDebug
\`\`\`

## 🔐 Implantação e Google Play

O código-fonte é público sob a Licença MIT. No entanto, o artefato de produção (`release.aab`) e as chaves de assinatura criptográficas (`keystore.jks`) são mantidos em sigilo através de `gitignore` para garantir a integridade da distribuição oficial na Google Play Store.
EOF
```
⚠️ **Atenção** Como este README é o "fechamento" da infraestrutura, agora você deve aplicar o ciclo GIT FLOW [✅]

```bash
# Adiciona o README  (se ainda não adicionou)
git add README.md

# Commit semântico de conclusão de seção
git commit -m "docs: finalizar documentação de apresentação"

# Sincroniza sua oficina com o GitHub
git push origin develop

```

⚠️ **Atenção** Agora você vai levar esse trabalho para a sua ramificação principal (main)  [✅]

```bash
# Vai para a branch de produção
git checkout main

# Traz as mudanças da develop para a main
git merge develop

# Atualiza o GitHub
git push origin main

# MUITO IMPORTANTE: Volta para a develop para começar a próxima fase
git checkout develop

``` 

### 4.6 Integração e Segurança para a Play Store [✅]

É vital reiterar que a publicação na Play Store é gerida **exclusivamente por você**, mantida totalmente separada do repositório público. O GitHub armazena apenas a lógica da aplicação.

Quando você estiver pronto para a Etapa de *Release* (que veremos mais à frente na Documentação):
1. A assinatura do artefato final (APK/AAB) será feita na sua máquina local utilizando uma chave privada (`.jks`).
2. Essa chave privada (`.jks`) e suas senhas (no arquivo `keystore.properties` ou em variáveis de ambiente) **NUNCA** devem ser enviadas ao GitHub. Nosso `.gitignore` já bloqueia esses arquivos.
3. Isso garante que ninguém possa compilar uma versão assinada legítima do seu app e alterar o aplicativo oficial listado sob o seu perfil de desenvolvedor na loja do Google.

### 4.7 Conventional Commits [✅]

Sempre use mensagens de commit no padrão:

```
feat: adicionar descoberta Samsung via mDNS
fix: corrigir crash no pareamento LG WebOS
docs: atualizar README com instruções de build
test: adicionar testes unitários para RokuConnector
chore: atualizar dependências Gradle
refactor: extrair lógica de WebSocket para classe base
```

---

## 5. Gradle: Dependências e Configurações [✅]

### 5.1 Catálogo de versões — `gradle/libs.versions.toml` [✅]

Crie ou substitua o arquivo `gradle/libs.versions.toml`:

```toml
[versions]
# ─── Ferramentas de build ────────────────────────────────────────────────────
# AGP 8.10+ é necessário para compilar com compileSdk = 36 (Android 16)
agp = "8.10.0"

# Kotlin 2.1.x + KSP compatível
# Kotlin 1.9.x funcionava até API 34; 2.1.x é necessário para API 35/36
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"              # deve ser sempre kotlin.version-x.y.z

# ─── Dependências principais ─────────────────────────────────────────────────
hilt = "2.51.1"
coroutines = "1.9.0"

# Compose BOM 2025.05+ inclui suporte nativo a Edge-to-Edge (Android 15)
# e layouts adaptativos para telas grandes (Android 16)
compose-bom = "2025.05.01"

# Navigation 2.9+ necessário para targetSdk = 35
navigation = "2.9.0"

retrofit = "2.11.0"
okhttp = "4.12.0"
room = "2.7.1"

# Lifecycle 2.9+ necessário para targetSdk = 35
lifecycle = "2.9.0"

# activity-compose 1.10+ adiciona enableEdgeToEdge() estável
# OBRIGATÓRIO para Android 15 (targetSdk = 35)
activity-compose = "1.10.1"

# core-ktx 1.16+ necessário para WindowInsetsCompat em Android 15
core-ktx = "1.16.0"

turbine = "1.2.0"
mockk = "1.13.16"

[libraries]
# AndroidX Core
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }

# Jetpack Compose (via BOM — não precisar especificar versão individual)
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }

# Hilt (Injeção de Dependência)
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# Room (Banco de Dados Local)
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Imagens Assíncronas
coil-compose = { group = "io.coil-kt", name = "coil-compose", version = "2.6.0" }

# Testes
junit = { group = "junit", name = "junit", version = "4.13.2" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
androidx-test-junit = { group = "androidx.test.ext", name = "junit", version = "1.1.5" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version = "3.5.1" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### 5.2 `settings.gradle.kts` (raiz do projeto) [✅]

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UniversalTVRemote"
include(":app")
```

### 5.3 `build.gradle.kts` (raiz do projeto) [✅]

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

### 5.4 `app/build.gradle.kts` (módulo app) [✅]

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.antoniosilva.universaltvremote"

    // compileSdk = 36 (Android 16): permite usar APIs do Android 16 no código
    // sem forçar os comportamentos do Android 16 em runtime.
    // Os comportamentos de runtime só mudam quando targetSdk = 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.antoniosilva.universaltvremote"

        // minSdk = 26 (Android 8.0): suporta ~99% dos dispositivos ativos.
        minSdk = 26

        // targetSdk = 35 (Android 15): alvo principal.
        //   - Exigido pela Google Play a partir de agosto de 2025.
        //   - Ativa comportamentos Android 15: Edge-to-Edge obrigatório,
        //     restrições de Foreground Service, TLS 1.0/1.1 bloqueado.
        //
        // FUTURO — Android 16 (API 36):
        //   Quando quiser migrar para Android 16, mude para targetSdk = 36.
        //   Impactos adicionais com targetSdk = 36:
        //     - Layouts adaptativos obrigatórios em displays > 600dp
        //       (o sistema ignora screenOrientation/resizeableActivity fixos).
        //     - Predictive Back Gestures ativados por padrão.
        //   Seu POCO Pad já será afetado por isso — garanta layouts responsivos.
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Configuração de assinatura (seção 18 - Documentação)
            // TODO: Implementar assinatura de release:  Como a chave criptográfica (.jks) ainda não foi provisionada no sistema de arquivos local, a sua invocação abortará a sincronização do Gradle. Mantenha comentado por enquanto Na IDE quando
           signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "keystore/universalremote.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "universalremote"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Depreciado desde o Kotlin 2.0, nova forma abaixo (DECLARAÇÃO DO COMPILADOR KOTLIN (K2) - Nova Estrutura)
    //kotlinOptions {
    //    jvmTarget = "17"
    //}

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // A partir do Kotlin 2.0+, o compilador do Jetpack Compose é gerido
    // nativamente pelo plugin org.jetbrains.kotlin.plugin.compose,
    // garantindo compatibilidade automática com a versão do Kotlin (K2).
    // O antigo bloco 'composeOptions' foi removido porque não é mais necessário.

    packaging {
        resources {
            // Exclui arquivos de metadados padrão para evitar conflitos de build
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

    // DECLARAÇÃO DO COMPILADOR KOTLIN (K2) - Nova Estrutura
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

dependencies {
    // === ANDROIDX CORE & LIFECYCLE ===
    implementation(libs.core.ktx) // Extensões básicas do Kotlin
    implementation(libs.lifecycle.runtime) // Gerenciamento visual do ciclo de vida da Activity
    implementation(libs.lifecycle.viewmodel) // Mantém dados ao girar tela
    implementation(libs.activity.compose) // Ponte entre a Activity antiga e a nova UI Compose

    // === JETPACK COMPOSE (UI) ===
    implementation(platform(libs.compose.bom)) // Gerencia globalmente as versões de todas libs Compose
    implementation(libs.compose.ui) // Desenho 2D de elementos
    implementation(libs.compose.ui.graphics) 
    implementation(libs.compose.ui.tooling.preview) // Renderiza a UI dentro do Android Studio
    implementation(libs.compose.material3) // Design System / Cores / Botões
    implementation(libs.compose.icons) // Pacote de Ícones estendido
    implementation(libs.compose.navigation) // Navegação entre "Telas" Compose
    implementation(libs.lifecycle.viewmodel.compose) // Injeção automática de viewModels() na UI
    debugImplementation(libs.compose.ui.tooling) // Ajuda no debug da pré-visualização

    // === NETWORK (Comunicação com a TV) ===
    implementation(libs.retrofit) // Simplifica chamadas HTTP (Roku API)
    implementation(libs.retrofit.gson) // Converte JSON para Classes Kotlin JSON -> Objeto
    implementation(libs.okhttp) // O motor HTTP base, gerencia timeouts, WebSocket etc
    implementation(libs.okhttp.logging) // Loga as requisições HTTP enviadas pro console (ótimo pra debug)

    // === DEPENDENCY INJECTION (Hilt) ===
    implementation(libs.hilt.android) // Passa dependências pra cima sem Boilerplate (ex: Repositórios pro VM)
    ksp(libs.hilt.compiler) // KSP: Processador que gera o código Hilt super rápido
    implementation(libs.hilt.navigation.compose) // Mantém singleton viewmodels integrados rotas de navegação Compose

    // === COROUTINES (Tarefas Assíncronas) ===
    implementation(libs.coroutines.core) // Threads de baixo custo para o DB e chamadas de Rede
    implementation(libs.coroutines.android) // Threads atreladas à UI Principal

    // === BANCO DE DADOS LOCAL (Room) ===
    implementation(libs.room.runtime) // Biblioteca do banco local base (SQLite Wrapper)
    implementation(libs.room.ktx) // Permite chamadas de BD usando coroutines (suspend functions)
    ksp(libs.room.compiler) // Compilador que cria todas requisições DAO na hora do build

    // === IMAGENS ASSÍNCRONAS ===
    implementation(libs.coil.compose) // Para baixar na net os ícones da Netflix, Youtube.

    // === TESTES UNITÁRIOS ===
    testImplementation(libs.junit) // Framework core de teste
    testImplementation(libs.mockk) // Moca objetos soltos (fakes de infra) nas classes Kotlin
    testImplementation(libs.turbine) // Facilita o teste de bibliotecas assíncronas Reactive Flow
    testImplementation(libs.coroutines.test)

    // === TESTES DE UI e INTEGRAÇÃO ===
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockwebserver) // Simula servidor REST pra mentir que somos a Roku TV WebServer
}
```

> ⚠️ **Correção importante**: A Doc-02 usava `kapt` para o processador de anotações do Hilt e Room. O correto para projetos novos é usar **KSP** (Kotlin Symbol Processing), que é mais rápido. Se o projeto foi criado com `kapt`, substitua todas as ocorrências de `kapt(...)` por `ksp(...)` e remova o plugin `kotlin-kapt`, adicionando o plugin `ksp` no lugar.

---

## 6. Permissões no AndroidManifest.xml

Abra `app/src/main/AndroidManifest.xml` e adicione as permissões necessárias:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Acesso à Internet (obrigatório para WebSocket e HTTP) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Estado do Wi-Fi -->
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <!-- Multicast (necessário para mDNS / descoberta de dispositivos) -->
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

    <!-- Estado da rede -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!--
        🛡️ SEGURANÇA / CASO EXTREMO:
        Android 13+ (API 33+): A permissão NEARBY_WIFI_DEVICES é uma 'runtime permission'.
        Seu app DEVE solicitar essa permissão explicitamente ao usuário através da UI
        (no Compose via accompanist-permissions ou ActivityResultLauncher) ANTES de 
        tentar buscar dispositivos com NsdManager/mDNS. 
        Se você apenas declarar aqui e não pedir, em dispositivos com Android 13 ou superior 
        (como o Galaxy S21 FE e POCO Pad), o app sofrerá um SecurityException e vai dar CRASH.
    -->
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />

    <!--
        Android 9 e abaixo: Localização necessária para descoberta de rede.
        maxSdkVersion="28" garante que NÃO será pedido no Android 9+
    -->
    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="28" />

    <application
        android:name=".UniversalTVRemoteApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.UniversalTVRemote"
        android:usesCleartextTraffic="true">
        <!--
            🛡️ SEGURANÇA VITAL PARA PRODUÇÃO:
            Evite usar android:usesCleartextTraffic="true" na tag <application> pois isso 
            libera conexões HTTP inseguras para o aplicativo todo, abrindo brechas 
            graves para ataques Man-in-the-Middle (MitM). 
            Como algumas TVs (ex: Roku, LG porta 3000) requerem de fato HTTP simples, a 
            maneira correta e segura de fazer isso é remover o usesCleartextTraffic daqui e 
            obrigatoriamente usar o network_security_config.xml para permitir HTTP 
            APENAS em IPs da rede local (192.168.x.x / 10.x.x.x).
        -->

        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.UniversalTVRemote">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

> ⚠️ **Network Security Config** — Para produção, crie `res/xml/network_security_config.xml` para especificar quais domínios/IPs podem usar HTTP simples, em vez de `usesCleartextTraffic="true"` global:

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Permite HTTP apenas na rede local (192.168.x.x, 10.x.x.x) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.1.0</domain>
    </domain-config>
    <!-- Todo o resto usa HTTPS -->
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

E no `AndroidManifest.xml`, substitua `usesCleartextTraffic` por:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```
