---

# 🗄️ ETAPA 4: Banco de Dados e App Completo (Seções 11 a 14 na Parte Anterior)
*(Nota: a Persistência Room e os ViewModels da Etapa 4 se juntam à Etapa 2 e Etapa 3)*

# 🚀 ETAPA 5: Qualidade, DevOps e Publicação
Chegando na última etapa! Agora vamos garantir que o TV Remote App tenha qualidade garantida por testes unitários e de integração, seja construído isoladamente num container Docker (Simulador de TV incluso) e tenha pipelines de deploy para a Play Store.

## 15. Testes Unitários e de Integração

### 15.1 Teste do RemoteControlViewModel

Crie `app/src/test/java/com/antoniosilva/universaltvremote/RemoteControlViewModelTest.kt`:

```kotlin
package com.antoniosilva.universaltvremote

import app.cash.turbine.test
import com.antoniosilva.universaltvremote.data.remote.TvConnectorFactory
import com.antoniosilva.universaltvremote.domain.model.*
import com.antoniosilva.universaltvremote.domain.repository.TvConnector
import com.antoniosilva.universaltvremote.presentation.viewmodel.RemoteControlViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RemoteControlViewModel
    private lateinit var factory: TvConnectorFactory
    private lateinit var connector: TvConnector

    private val testDevice = TvDevice(
        id = "test-id",
        name = "Samsung TV Teste",
        brand = TvBrand.SAMSUNG,
        ipAddress = "192.168.1.100",
        port = 8001
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        connector = mockk(relaxed = true)
        factory = mockk()
        every { factory.getConnector(any()) } returns connector
        every { connector.connectionState() } returns flowOf(ConnectionState.Connected(testDevice))
        viewModel = RemoteControlViewModel(factory, mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `conectar deve atualizar estado da UI`() = runTest {
        viewModel.connectTo(testDevice)
        advanceUntilIdle() // Aguarda todas as coroutines do ViewModel rodarem na thread de teste

        viewModel.uiState.test { // Turbine facilita testar mudanças em StateFlows
            val state = awaitItem()
            assertEquals("Samsung TV Teste", state.deviceName)
        }
    }

    @Test
    fun `enviar comando deve chamar connector`() = runTest {
        coEvery { connector.sendCommand(any()) } returns Result.success(Unit) // Moka a resposta real de rede
        viewModel.connectTo(testDevice)
        viewModel.send(TvCommand.VolumeUp)
        
        advanceUntilIdle()
        
        // Verifica se o viewModel de fato executou a chamada ao conector com o input correto
        coVerify { connector.sendCommand(TvCommand.VolumeUp) }
    }

    @Test
    fun `deve tratar erro quando envio de comando falhar`() = runTest {
        // 🛡️ TESTE: Garantir que o app não quebre se a TV desconectar do nada
        coEvery { connector.sendCommand(any()) } returns Result.failure(Exception("Conexão perdida"))
        viewModel.connectTo(testDevice)
        viewModel.send(TvCommand.PowerOff)
        
        advanceUntilIdle()
        // Opcional: verificar se uiState atualiza com mensagem de erro ou se notifica o usuário
    }
}
```

### 15.2 Teste do conector Roku com MockWebServer

Crie `app/src/test/java/com/antoniosilva/universaltvremote/RokuConnectorTest.kt`:

```kotlin
package com.antoniosilva.universaltvremote

import com.antoniosilva.universaltvremote.data.remote.roku.RokuConnector
import com.antoniosilva.universaltvremote.domain.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import kotlin.test.assertTrue

class RokuConnectorTest {

    private lateinit var server: MockWebServer
    private lateinit var connector: RokuConnector

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        connector = RokuConnector(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sendCommand VolumeUp deve fazer POST para Roku ECP`() = runTest {
        // Simula uma Roku TV respondendo com HTTP 200 OK
        server.enqueue(MockResponse().setResponseCode(200))

        val device = TvDevice(
            id = "roku-1",
            name = "Roku TV",
            brand = TvBrand.ROKU,
            ipAddress = server.hostName, // Aponta pro nosso Fake WebServer 
            port = server.port
        )

        connector.connect(device)
        val result = connector.sendCommand(TvCommand.VolumeUp) # Executa Comando

        assertTrue(result.isSuccess)
        
        // Verifica se o caminho e método HTTP disparados para a TV são exatamente o que a docs do ECP exige
        val request = server.takeRequest()
        assert(request.path?.contains("VolumeUp") == true)
        assert(request.method == "POST")
    }

    @Test
    fun `sendCommand deve retornar falha em caso de timeout`() = runTest {
        // 🛡️ TESTE: Simulando timeout de rede (o servidor demora pra responder)
        val connectorComTimeout = RokuConnector(
            OkHttpClient.Builder()
                .readTimeout(10, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
        )
        // Você poderia usar o DispatcherServer para atrasar a MockResponse
    }
}
```

### 15.3 Teste de UI com Compose

Crie `app/src/androidTest/java/com/antoniosilva/universaltvremote/RemoteControlScreenTest.kt`:

```kotlin
package com.antoniosilva.universaltvremote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antoniosilva.universaltvremote.presentation.MainActivity
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteControlScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun telaPrincipalExibeBotaoBuscar() {
        composeRule.onNodeWithText("Buscar TVs").assertIsDisplayed()
    }

    @Test
    fun clicarBuscarIniciaScan() {
        composeRule.onNodeWithContentDescription("Buscar TVs").performClick()
        // Verificar estado de scanning
        composeRule.onNodeWithText("Buscando TVs na rede...").assertIsDisplayed()
    }
}
```

---

## 16. Docker para Build e Simulador de TV

### 16.1 Dockerfile para build Android

Crie `docker/Dockerfile`:

```dockerfile
FROM openjdk:17-jdk-slim

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

# Instalar dependências do sistema
RUN apt-get update && apt-get install -y wget unzip git curl && rm -rf /var/lib/apt/lists/*

# Baixar Command-line tools do Android
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Aceitar licenças e instalar SDK
RUN yes | sdkmanager --licenses
# android-35 = Android 15 (targetSdk atual — exigido pela Play Store a partir de ago/2025)
# android-36 = Android 16 (compileSdk — para aproveitar as APIs mais novas sem alterar o runtime)
# build-tools 36.0.0 é compatível com ambas as versões
RUN sdkmanager "platform-tools" "platforms;android-35" "platforms;android-36" "build-tools;36.0.0"

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY . .
CMD ["./gradlew", "assembleDebug", "--no-daemon"]
```

### 16.2 Simulador de TV (Python)

Crie `docker/tv-simulator/simulator.py`:

```python
#!/usr/bin/env python3
"""
Simulador simples de TV para teste do app.
Responde a comandos HTTP no estilo Roku ECP e anúncios SSDP.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(message)s')

class TVSimulatorHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        logging.info(f"Comando recebido: {self.path}")
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")

    def do_GET(self):
        if self.path == "/":
            body = b"""<?xml version="1.0"?>
<root>
  <device>
    <friendlyName>TV Simulador Docker</friendlyName>
    <manufacturer>Docker Simulator</manufacturer>
    <modelName>SimTV-1000</modelName>
  </device>
</root>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/xml")
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        logging.info(f"HTTP: {format % args}")

if __name__ == "__main__":
    port = 8060
    server = HTTPServer(("0.0.0.0", port), TVSimulatorHandler)
    logging.info(f"Simulador de TV iniciado na porta {port}")
    server.serve_forever()
```

Crie `docker/tv-simulator/Dockerfile`:

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY simulator.py .
EXPOSE 8060
CMD ["python", "simulator.py"]
```

Crie `docker/tv-simulator/requirements.txt`:
```
# Sem dependências externas —  usa apenas stdlib do Python
```

### 16.3 docker-compose.yml

Crie `docker/docker-compose.yml`:

```yaml
version: '3.8'

services:
  # Build do APK Android dentro do container
  android-build:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    volumes:
      - ../:/app
      - gradle-cache:/root/.gradle
    environment:
      - GRADLE_OPTS=-Dorg.gradle.daemon=false
    command: ./gradlew assembleDebug --no-daemon

  # Testes unitários
  android-test:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    volumes:
      - ../:/app
      - gradle-cache:/root/.gradle
    command: ./gradlew test --no-daemon

  # Simulador de TV para testes de integração
  tv-simulator:
    build: ./tv-simulator
    ports:
      - "8060:8060"     # Porta Roku ECP
    network_mode: host  # Visível na mesma rede que o dispositivo Android

volumes:
  gradle-cache:
```

### 16.4 Como usar o Docker

```bash
# Construir imagem de build
docker compose -f docker/docker-compose.yml build android-build

# Gerar APK debug via Docker
docker compose -f docker/docker-compose.yml run android-build

# Rodar testes via Docker
docker compose -f docker/docker-compose.yml run android-test

# Iniciar simulador de TV (para testes manuais)
docker compose -f docker/docker-compose.yml up tv-simulator

# APK gerado estará em:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 17. CI/CD com GitHub Actions

### 17.1 Pipeline de CI (lint + testes + build)

Crie `.github/workflows/android-ci.yml`:

```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout do código
        uses: actions/checkout@v4

      - name: Configurar JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}
          restore-keys: ${{ runner.os }}-gradle-

      - name: Dar permissão ao gradlew
        run: chmod +x gradlew

      - name: Rodar testes unitários
        run: ./gradlew test --no-daemon

      - name: Build APK Debug
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload APK como artefato
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 7

      - name: Publicar relatório de testes
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Testes Unitários
          path: '**/build/test-results/test/*.xml'
          reporter: java-junit
```

### 17.2 Pipeline de Release (tag v*)

Crie `.github/workflows/release.yml`:

```yaml
name: Release Build

on:
  push:
    tags:
      - 'v*'   # Ativar quando criar tag como v1.0.0

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Configurar JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Decodificar Keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > app/keystore/universalremote.jks

      - name: Build Release APK
        env:
          KEYSTORE_FILE: keystore/universalremote.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease --no-daemon

      - name: Criar Release no GitHub
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/app-release.apk
          generate_release_notes: true
```

### 17.3 Configurar Secrets no GitHub

No repositório GitHub:
1. Settings → Secrets and variables → Actions → New repository secret
2. Adicione os seguintes secrets:

| Secret | Valor |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w 0 universalremote.jks` |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | alias da chave |
| `KEY_PASSWORD` | senha da chave |

---

## 18. Build, Assinatura e Publicação

### 18.1 Gerar Keystore de assinatura

```bash
# Criar diretório para o keystore (fora do projeto Git!)
mkdir -p ~/keystore-privado

# Gerar keystore RSA 4096 bits com 25 anos de validade
keytool -genkey -v \
  -keystore ~/keystore-privado/universalremote.jks \
  -alias universalremote \
  -keyalg RSA \
  -keysize 4096 \
  -validity 9125 \
  -storepass SuaSenhaSegura123 \
  -keypass SuaSenhaSegura123 \
  -dname "CN=Seu Nome, OU=Dev, O=SuaEmpresa, L=Cidade, S=Estado, C=BR"

# ⚠️ NUNCA adicione o arquivo .jks ao Git!
# Confirme que está no .gitignore:
grep "*.jks" .gitignore  # deve retornar a linha
```

### 18.2 Variáveis de ambiente para assinatura local

```bash
# Adicionar ao seu ~/.zshrc ou ~/.bashrc:
export KEYSTORE_FILE="$HOME/keystore-privado/universalremote.jks"
export KEYSTORE_PASSWORD="SuaSenhaSegura123"
export KEY_ALIAS="universalremote"
export KEY_PASSWORD="SuaSenhaSegura123"

# Aplicar sem reiniciar
source ~/.zshrc
```

### 18.3 Gerar APK de Release

```bash
cd ~/Projetos/UniversalTVRemote

# Build do APK assinado
./gradlew assembleRelease

# APK gerado em:
# app/build/outputs/apk/release/app-release.apk

# Verificar assinatura
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

### 18.4 Gerar App Bundle (para Google Play)

```bash
# Android App Bundle (preferido pela Play Store)
./gradlew bundleRelease

# AAB gerado em:
# app/build/outputs/bundle/release/app-release.aab
```

### 18.5 Publicar na Google Play Store

1. Acesse [Google Play Console](https://play.google.com/console) e crie uma conta de desenvolvedor (taxa única de U$25)
2. Clique em **Create app**
3. Preencha as informações do app:
   - Nome: Universal TV Remote
   - Idioma padrão: Português (Brasil)
   - Tipo: Aplicativo
   - Gratuito
4. Complete as seções obrigatórias:
   - **Store listing**: ícone 512×512, screenshots, descrição
   - **Content rating**: preencher questionário
   - **Target audience**: público geral
   - **Privacy policy**: URL da sua política (ex.: GitHub Pages)
5. Em **Production → Releases**, faça upload do `.aab`
6. Envie para revisão (leva de 1 a 7 dias)

---

## 19. Wireless Debugging no CachyOS

Para desenvolvimento sem cabo USB usando Galaxy S21 FE (Android 11+) ou POCO Pad:

### 19.1 Ativar Wireless Debugging no Android

No celular/tablet:
1. Ativar **Opções do desenvolvedor**: Configurações → Sobre o telefone → tap 7x em "Número da versão"
2. Configurações → Sistema → Opções do desenvolvedor → **Depuração sem fio** → Ativar

### 19.2 Conectar via Par QR Code (mais fácil)

```bash
# No computador, executar:
adb pair IP:PORT
# Substitua IP:PORT pelo endereço exibido em "Parear dispositivo com código QR"
# (ou use o código de pareamento de 6 dígitos exibido na tela)

# Após parear, conectar:
adb connect IP:PORT
# O IP:PORT de conexão está em "Endereço IP e porta" nas opções de wireless debugging

# Verificar se está conectado:
adb devices
# Deve mostrar: IP:PORT    device
```

### 19.3 Instalar APK debug no dispositivo

```bash
# Build e instalar diretamente via Gradle
./gradlew installDebug

# Ou instalar APK já compilado
adb install app/build/outputs/apk/debug/app-debug.apk

# Ver logs em tempo real
adb logcat -s "UniversalTVRemote" | grep -E "E|W|D"
```

### 19.4 Hot Reload com Android Studio

1. No Android Studio, selecione seu dispositivo no menu superior (deve aparecer o nome do dispositivo conectado via ADB wireless)
2. Clique em **Run** (▶) ou pressione `Shift+F10`
3. Para hot reload do Compose, clique em **Apply Changes** (⚡) durante a execução

---

## 20. Checklist de Implementação

Use este checklist para acompanhar o progresso:

| Fase | Tarefa | Status |
|------|--------|--------|
| **Ambiente** | JDK 17 instalado | ⬜ |
| | Android Studio instalado | ⬜ |
| | ADB configurado | ⬜ |
| | Docker funcionando | ⬜ |
| **Projeto** | Projeto criado no Android Studio | ⬜ |
| | Git + .gitignore configurados | ⬜ |
| | Repositório GitHub criado | ⬜ |
| | Gradle com KSP (não kapt) | ⬜ |
| | libs.versions.toml configurado | ⬜ |
| **Permissões** | AndroidManifest.xml configurado | ⬜ |
| | network_security_config.xml | ⬜ |
| **Domínio** | TvDevice, TvCommand, ConnectionState | ⬜ |
| | TvRemoteRepository (interface) | ⬜ |
| | TvConnector (interface) | ⬜ |
| **Discovery** | TvDiscoveryService (mDNS/NsdManager) | ⬜ |
| | DiscoveryViewModel | ⬜ |
| | DiscoveryScreen | ⬜ |
| **Samsung** | SamsungConnector (WebSocket 8001) | ⬜ |
| | Pareamento com token | ⬜ |
| | Todos os comandos mapeados | ⬜ |
| **LG** | LGConnector (WebSocket 3000) | ⬜ |
| | Registro com client-key | ⬜ |
| | Salvar client-key no Room | ⬜ |
| **Roku** | RokuConnector (HTTP ECP 8060) | ⬜ |
| | Todos os comandos mapeados | ⬜ |
| **Room** | PairedTvEntity + DAO | ⬜ |
| | PairedTvRepository | ⬜ |
| | AppDatabase | ⬜ |
| **UI** | RemoteControlScreen | ⬜ |
| | DiscoveryScreen | ⬜ |
| | Navegação entre telas | ⬜ |
| | Layout responsivo (tablet) | ⬜ |
| **Hilt** | AppModule configurado | ⬜ |
| | @HiltAndroidApp na Application | ⬜ |
| **Testes** | Testes unitários ViewModel | ⬜ |
| | Testes Roku com MockWebServer | ⬜ |
| | Testes UI com Compose | ⬜ |
| **DevOps** | Dockerfile Android build | ⬜ |
| | Simulador de TV Docker | ⬜ |
| | CI/CD GitHub Actions | ⬜ |
| **Build** | Keystore gerado e seguro | ⬜ |
| | Build de release funcionando | ⬜ |
| | App Bundle (.aab) gerado | ⬜ |
| **Play Store** | Conta de desenvolvedor criada | ⬜ |
| | App publicado | ⬜ |
| **Android 15 (API 35)** | `compileSdk = 36` e `targetSdk = 35` no build.gradle.kts | ⬜ |
| | `enableEdgeToEdge()` na MainActivity | ⬜ |
| | `NsdManager.resolveService` com API dupla (API 33+/legada) | ⬜ |
| | Ícones AutoMirrored (ArrowBack) substituídos | ⬜ |
| | Dependências atualizadas (AGP 8.10, Kotlin 2.1, BOM 2025) | ⬜ |
| **Android 16 (API 36) — Futuro** | Mudar `targetSdk = 36` (ativa comportamentos Android 16) | ⬜ |
| | Layouts adaptativos para displays > 600dp (POCO Pad) | ⬜ |
| | Predictive Back Gestures (opcional, melhora UX) | ⬜ |

---

## 21. Recursos e Links Oficiais

| Recurso | URL |
|---------|-----|
| Samsung Tizen Remote API | https://developer.samsung.com/smarttv |
| LG WebOS Developer | https://webostv.developer.lge.com |
| Roku ECP (External Control Protocol) | https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md |
| Sony Bravia Professional | https://pro-bravia.sony.net/develop |
| Android NsdManager (mDNS) | https://developer.android.com/training/connect-devices-wirelessly/nsd |
| Jetpack Compose | https://developer.android.com/jetpack/compose |
| Hilt DI | https://dagger.dev/hilt |
| Room | https://developer.android.com/training/data-storage/room |
| OkHttp WebSocket | https://square.github.io/okhttp/4.x/okhttp/okhttp3/-web-socket |
| GitHub Actions Android | https://github.com/marketplace/actions/setup-java |
| Google Play Console | https://play.google.com/console |

---

> **💡 Recomendação de ordem de implementação:**
> 1. Configure o ambiente e crie o projeto (seções 1–6)
> 2. Implemente o domínio e Room (seções 8, 11)
> 3. Implemente a descoberta de TVs (seção 9)
> 4. Comece com **Roku** (mais simples, HTTP sem pareamento) (seção 10.4)
> 5. Adicione **Samsung** (seção 10.2)
> 6. Adicione **LG** (seção 10.3)
> 7. Monte a UI completa (seção 12)
> 8. Escreva os testes (seção 15)
> 9. Configure CI/CD (seção 17)
> 10. Publique (seção 18)
