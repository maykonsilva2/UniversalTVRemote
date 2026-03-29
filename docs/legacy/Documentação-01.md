Vou criar um guia completo para o desenvolvimento do seu **Universal TV Remote App** em Kotlin. Este projeto será complexo devido às diferentes APIs de cada fabricante, então vou estruturar de forma profissional com Git, Docker e CI/CD.

## 📋 Índice do Projeto

1. [Arquitetura e Estrutura](#1-arquitetura-e-estrutura)
2. [Configuração do Ambiente](#2-configuração-do-ambiente)
3. [Implementação por Módulos](#3-implementação)
4. [Infraestrutura DevOps](#4-infraestrutura-devops)
5. [Testes e Deploy](#5-testes-e-deploy)

---

## 1. Arquitetura e Estrutura

### Estrutura de Pastas do Projeto

```
tv-remote-app/
├── 📁 .github/
│   └── workflows/
│       ├── android-ci.yml          # CI para build e testes
│       └── release.yml             # CD para Google Play
├── 📁 docker/
│   ├── Dockerfile                  # Ambiente de build Android
│   └── docker-compose.yml          # Serviços auxiliares
├── 📁 docs/
│   ├── api-references/             # Documentações oficiais das TVs
│   └── architecture.md
├── 📁 app/                         # Módulo principal Android
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tvremote/
│   │   │   │   ├── data/           # Camada de dados
│   │   │   │   ├── domain/         # Lógica de negócio
│   │   │   │   ├── presentation/   # UI (MVVM)
│   │   │   │   └── di/             # Injeção de dependência (Hilt)
│   │   │   └── res/
│   │   └── test/                   # Testes unitários
│   └── build.gradle.kts
├── 📁 core/                        # Módulo core (compartilhado)
├── 📁 tv-brands/                   # Módulos por fabricante
│   ├── samsung/                    # API Tizen
│   ├── lg/                         # API WebOS
│   ├── roku/                       # API Roku ECP
│   └── sony/                       # API Bravia
├── 📁 discovery/                   # Módulo de descoberta de rede
├── gradle/
├── .gitignore
├── README.md
└── build.gradle.kts
```

---

## 2. Configuração do Ambiente

### 2.1 Inicialização do Repositório Git

```bash
# Criar estrutura do projeto
mkdir tv-remote-app && cd tv-remote-app
git init

# Configurar Git Flow
git checkout -b main
git checkout -b develop

# Criar .gitignore completo
cat > .gitignore << 'EOF'
# Gradle
.gradle/
build/
*/build/
gradle-app.setting
!gradle-wrapper.jar
!gradle-wrapper.properties

# Android Studio
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

# OS
.DS_Store
Thumbs.db

# Secrets (nunca commitar!)
secrets.properties
keystore.properties
*.jks
*.keystore
google-services.json

# Docker
.dockerignore
EOF

git add .gitignore
git commit -m "chore: initial commit with gitignore"
```

### 2.2 Configuração do Docker para Build

**`docker/Dockerfile`**:
```dockerfile
# Imagem oficial do Android SDK
FROM androidsdk/android-30:latest

# Configuração do ambiente
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Instalar dependências do sistema
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    git \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Configurar Kotlin/Gradle
WORKDIR /app

# Copiar arquivos de build primeiro (cache de camadas)
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./
COPY settings.gradle.kts build.gradle.kts ./

# Baixar dependências (cache)
RUN ./gradlew dependencies --no-daemon || true

# Copiar código fonte
COPY . .

# Comando padrão
CMD ["./gradlew", "assembleDebug", "--no-daemon"]
```

**`docker/docker-compose.yml`**:
```yaml
version: '3.8'

services:
  # Serviço de build Android
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

  # Serviço para testes unitários
  android-test:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    volumes:
      - ../:/app
      - gradle-cache:/root/.gradle
    command: ./gradlew test --no-daemon

  # Serviço de lint e qualidade
  android-lint:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    volumes:
      - ../:/app
      - gradle-cache:/root/.gradle
    command: ./gradlew ktlintCheck detekt --no-daemon

volumes:
  gradle-cache:
```

### 2.3 Configuração Gradle Multi-Módulo

**`settings.gradle.kts`**:
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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "UniversalTVRemote"
include(":app")
include(":core")
include(":discovery")
include(":tv-brands:samsung")
include(":tv-brands:lg")
include(":tv-brands:roku")
include(":tv-brands:sony")
```

**`build.gradle.kts` (root)**:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Definição de versões centralizada
extra["compileSdk"] = 34
extra["minSdk"] = 26
extra["targetSdk"] = 34
```

**`gradle/libs.versions.toml`**:
```toml
[versions]
agp = "8.2.0"
kotlin = "1.9.21"
hilt = "2.48"
coroutines = "1.7.3"
compose = "1.5.4"
retrofit = "2.9.0"
okhttp = "4.12.0"
room = "2.6.1"

[libraries]
# AndroidX
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.12.0" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version = "2.6.2" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.8.1" }

# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version = "2023.10.01" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version = "2.7.5" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.1.0" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Database
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Network Discovery
jmdns = { group = "org.jmdns", name = "jmdns", version = "3.5.8" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.8" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "1.9.21-1.0.15" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version = "11.6.1" }
detekt = { id = "io.gitlab.arturbosch.detekt", version = "1.23.3" }
```

---

## 3. Implementação

### 3.1 Módulo Core (Contratos e Utilidades)

**`core/src/main/java/com/tvremote/core/model/TvDevice.kt`**:
```kotlin
package com.tvremote.core.model

import java.util.UUID

data class TVDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ipAddress: String,
    val port: Int,
    val brand: TVBrand,
    val model: String? = null,
    val isPaired: Boolean = false,
    val macAddress: String? = null
)

enum class TVBrand {
    SAMSUNG, LG, ROKU, SONY, UNKNOWN
}

sealed class RemoteCommand {
    // Controles básicos
    object PowerOn : RemoteCommand()
    object PowerOff : RemoteCommand()
    object VolumeUp : RemoteCommand()
    object VolumeDown : RemoteCommand()
    object Mute : RemoteCommand()
    object ChannelUp : RemoteCommand()
    object ChannelDown : RemoteCommand()
    
    // Navegação
    object Up : RemoteCommand()
    object Down : RemoteCommand()
    object Left : RemoteCommand()
    object Right : RemoteCommand()
    object OK : RemoteCommand()
    object Back : RemoteCommand()
    object Home : RemoteCommand()
    object Menu : RemoteCommand()
    
    // Mídia
    object Play : RemoteCommand()
    object Pause : RemoteCommand()
    object Stop : RemoteCommand()
    object FastForward : RemoteCommand()
    object Rewind : RemoteCommand()
    
    // Entrada de texto
    data class SendText(val text: String) : RemoteCommand()
    
    // Abrir app
    data class LaunchApp(val appId: String) : RemoteCommand()
    
    // Número do canal
    data class ChannelNumber(val number: Int) : RemoteCommand()
}
```

**`core/src/main/java/com/tvremote/core/repository/TVRemoteRepository.kt`**:
```kotlin
package com.tvremote.core.repository

import com.tvremote.core.model.RemoteCommand
import com.tvremote.core.model.TVDevice
import kotlinx.coroutines.flow.Flow

interface TVRemoteRepository {
    suspend fun connect(device: TVDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun sendCommand(command: RemoteCommand): Result<Unit>
    suspend fun getInstalledApps(): Result<List<TVApp>>
    fun connectionState(): Flow<ConnectionState>
}

data class TVApp(
    val id: String,
    val name: String,
    val iconUrl: String?
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: TVDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

### 3.2 Módulo Discovery (Descoberta de Rede)

**`discovery/src/main/java/com/tvremote/discovery/TVDiscoveryService.kt`**:
```kotlin
package com.tvremote.discovery

import com.tvremote.core.model.TVBrand
import com.tvremote.core.model.TVDevice
import kotlinx.coroutines.flow.Flow
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import java.net.InetAddress

class TVDiscoveryService {
    
    private var jmdns: JmDNS? = null
    private val serviceTypes = mapOf(
        "_samsung-remocon._tcp.local." to TVBrand.SAMSUNG,
        "_lg-smart-tv._tcp.local." to TVBrand.LG,
        "_roku-ecp._tcp.local." to TVBrand.ROKU,
        "_sony-rc._tcp.local." to TVBrand.SONY
    )

    suspend fun startDiscovery(onDeviceFound: (TVDevice) -> Unit) {
        jmdns = JmDNS.create(InetAddress.getLocalHost())
        
        serviceTypes.forEach { (serviceType, brand) ->
            jmdns?.addServiceListener(serviceType, object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent?) {}
                
                override fun serviceRemoved(event: ServiceEvent?) {}
                
                override fun serviceResolved(event: ServiceEvent?) {
                    event?.info?.let { info ->
                        val device = TVDevice(
                            name = info.name,
                            ipAddress = info.inetAddresses.firstOrNull()?.hostAddress ?: return,
                            port = info.port,
                            brand = brand,
                            model = info.getPropertyString("model")
                        )
                        onDeviceFound(device)
                    }
                }
            })
        }
    }

    fun stopDiscovery() {
        jmdns?.unregisterAllServices()
        jmdns?.close()
    }
}
```

### 3.3 Implementação Samsung (Tizen API)

**`tv-brands/samsung/src/main/java/com/tvremote/samsung/SamsungRemoteRepository.kt`**:
```kotlin
package com.tvremote.samsung

import com.tvremote.core.model.RemoteCommand
import com.tvremote.core.model.TVDevice
import com.tvremote.core.repository.TVRemoteRepository
import com.tvremote.core.repository.ConnectionState
import com.tvremote.core.repository.TVApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SamsungRemoteRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) : TVRemoteRepository {

    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    override suspend fun connect(device: TVDevice): Result<Unit> {
        return try {
            // Samsung usa WebSocket na porta 8002 (SSL) ou 8001
            val wsUrl = "wss://${device.ipAddress}:8002/api/v2/channels/samsung.remote.control"
            
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    // Enviar handshake com nome do app
                    val handshake = JSONObject().apply {
                        put("method", "ms.remote.channel")
                        put("params", JSONObject().apply {
                            put("data", JSONObject().apply {
                                put("Channel", "1")
                                put("Event", "DID")
                                put("To", "host")
                            })
                            put("to", "host")
                            put("token", getTokenForDevice(device)) // Token de pareamento
                        })
                    }
                    webSocket.send(handshake.toString())
                    _connectionState.value = ConnectionState.Connected(device)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            })
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val keyCode = mapCommandToSamsungKey(command)
        
        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", keyCode)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }
        
        return try {
            webSocket?.send(payload.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getInstalledApps(): Result<List<TVApp>> {
        // Samsung API específica para listar apps
        return try {
            val request = Request.Builder()
                .url("http://${webSocket?.request()?.url?.host}:8001/api/v2/applications")
                .build()
            
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "[]")
            val apps = json.getJSONArray("data").let { array ->
                (0 until array.length()).map { i ->
                    val app = array.getJSONObject(i)
                    TVApp(
                        id = app.getString("id"),
                        name = app.getString("name"),
                        iconUrl = app.optString("icon")
                    )
                }
            }
            Result.success(apps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapCommandToSamsungKey(command: RemoteCommand): String {
        return when (command) {
            is RemoteCommand.PowerOn -> "KEY_POWER"
            is RemoteCommand.PowerOff -> "KEY_POWER"
            is RemoteCommand.VolumeUp -> "KEY_VOLUP"
            is RemoteCommand.VolumeDown -> "KEY_VOLDOWN"
            is RemoteCommand.Mute -> "KEY_MUTE"
            is RemoteCommand.ChannelUp -> "KEY_CHUP"
            is RemoteCommand.ChannelDown -> "KEY_CHDOWN"
            is RemoteCommand.Up -> "KEY_UP"
            is RemoteCommand.Down -> "KEY_DOWN"
            is RemoteCommand.Left -> "KEY_LEFT"
            is RemoteCommand.Right -> "KEY_RIGHT"
            is RemoteCommand.OK -> "KEY_ENTER"
            is RemoteCommand.Back -> "KEY_RETURN"
            is RemoteCommand.Home -> "KEY_HOME"
            is RemoteCommand.Menu -> "KEY_MENU"
            is RemoteCommand.Play -> "KEY_PLAY"
            is RemoteCommand.Pause -> "KEY_PAUSE"
            is RemoteCommand.Stop -> "KEY_STOP"
            is RemoteCommand.FastForward -> "KEY_FF"
            is RemoteCommand.Rewind -> "KEY_REWIND"
            is RemoteCommand.SendText -> "KEY_${command.text}" // Requer tratamento especial
            is RemoteCommand.LaunchApp -> "KEY_HOME" // Navegar para app específico
            is RemoteCommand.ChannelNumber -> "KEY_${command.number}"
        }
    }

    private fun getTokenForDevice(device: TVDevice): String? {
        // Implementar recuperação de token do DataStore/EncryptedSharedPreferences
        return null
    }
}
```

### 3.4 Implementação LG (WebOS)

**`tv-brands/lg/src/main/java/com/tvremote/lg/LGRemoteRepository.kt`**:
```kotlin
package com.tvremote.lg

import com.tvremote.core.model.RemoteCommand
import com.tvremote.core.model.TVDevice
import com.tvremote.core.repository.TVRemoteRepository
import com.tvremote.core.repository.ConnectionState
import com.tvremote.core.repository.TVApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

class LGRemoteRepository(
    private val client: OkHttpClient
) : TVRemoteRepository {

    private var webSocket: WebSocket? = null
    private var clientKey: String? = null // Chave de pareamento
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    override suspend fun connect(device: TVDevice): Result<Unit> {
        val wsUrl = "ws://${device.ipAddress}:3000/"
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                // Registrar app com WebOS
                val registerPayload = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("type", "register")
                    put("payload", JSONObject().apply {
                        put("forcePairing", false)
                        put("pairingType", "PROMPT")
                        put("manifest", JSONObject().apply {
                            put("manifestVersion", 1)
                            put("appVersion", "1.0")
                            put("signed", JSONObject())
                            put("permissions", listOf(
                                "LAUNCH",
                                "LAUNCH_WEBAPP",
                                "APP_TO_APP",
                                "CONTROL_AUDIO",
                                "CONTROL_INPUT_MEDIA_PLAYBACK",
                                "CONTROL_POWER",
                                "READ_CURRENT_CHANNEL",
                                "READ_INPUT_DEVICE_LIST",
                                "READ_NETWORK_STATE",
                                "READ_RUNNING_APPS",
                                "READ_TV_CHANNEL_LIST",
                                "WRITE_NOTIFICATION_TOAST",
                                "CONTROL_DISPLAY",
                                "CONTROL_INPUT_JOYSTICK",
                                "CONTROL_INPUT_MEDIA_RECORDING",
                                "CONTROL_INPUT_TV",
                                "READ_INSTALLED_APPS",
                                "READ_LGE_SDX",
                                "READ_NOTIFICATION_TOAST",
                                "SEARCH",
                                "WRITE_SETTINGS",
                                "WRITE_LGE_SDX"
                            ))
                            put("signed", JSONObject().apply {
                                put("created", System.currentTimeMillis())
                                put("appId", "com.tvremote.universal")
                                put("vendorId", "com.tvremote")
                                put("localizedAppNames", JSONObject().apply {
                                    put("", "Universal TV Remote")
                                })
                                put("localizedVendorNames", JSONObject().apply {
                                    put("", "TV Remote Inc")
                                })
                                put("permissions", JSONArray().apply {
                                    put("TEST_SECURE")
                                    put("CONTROL_INPUT_TEXT")
                                    put("CONTROL_MOUSE_AND_BUTTONS")
                                    put("READ_INSTALLED_APPS")
                                    put("LAUNCH")
                                    put("LAUNCH_WEBAPP")
                                    put("APP_TO_APP")
                                    put("CONTROL_AUDIO")
                                    put("CONTROL_INPUT_MEDIA_PLAYBACK")
                                    put("CONTROL_POWER")
                                    put("READ_CURRENT_CHANNEL")
                                    put("READ_INPUT_DEVICE_LIST")
                                    put("READ_NETWORK_STATE")
                                    put("READ_RUNNING_APPS")
                                    put("READ_TV_CHANNEL_LIST")
                                    put("WRITE_NOTIFICATION_TOAST")
                                    put("CONTROL_DISPLAY")
                                    put("CONTROL_INPUT_JOYSTICK")
                                    put("CONTROL_INPUT_MEDIA_RECORDING")
                                    put("CONTROL_INPUT_TV")
                                    put("READ_LGE_SDX")
                                    put("READ_NOTIFICATION_TOAST")
                                    put("SEARCH")
                                    put("WRITE_SETTINGS")
                                    put("WRITE_LGE_SDX")
                                })
                            })
                        })
                        put("client-key", clientKey)
                    })
                }
                webSocket.send(registerPayload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val response = JSONObject(text)
                when (response.optString("type")) {
                    "registered" -> {
                        // Salvar client-key para futuras conexões
                        clientKey = response.getJSONObject("payload").getString("client-key")
                        _connectionState.value = ConnectionState.Connected(device)
                    }
                    "error" -> {
                        if (response.getString("error") == "403 pairing denied") {
                            // Mostrar PIN na TV, aguardar confirmação
                            _connectionState.value = ConnectionState.Error("Aguardando confirmação na TV")
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
            }
        })
        
        return Result.success(Unit)
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> {
        val uri = when (command) {
            is RemoteCommand.PowerOff -> "ssap://system/turnOff"
            is RemoteCommand.VolumeUp -> "ssap://audio/volumeUp"
            is RemoteCommand.VolumeDown -> "ssap://audio/volumeDown"
            is RemoteCommand.Mute -> "ssap://audio/setMute"
            is RemoteCommand.Up -> "ssap://com.webos.service.ime/sendEnterKey"
            is RemoteCommand.Down -> "ssap://com.webos.service.ime/sendEnterKey"
            is RemoteCommand.Left -> "ssap://com.webos.service.ime/sendEnterKey"
            is RemoteCommand.Right -> "ssap://com.webos.service.ime/sendEnterKey"
            is RemoteCommand.OK -> "ssap://com.webos.service.ime/sendEnterKey"
            is RemoteCommand.Back -> "ssap://com.webos.service.ime/sendEnterKey"
            is RemoteCommand.Home -> "ssap://com.webos.applicationManager/launch"
            is RemoteCommand.Play -> "ssap://media.controls/play"
            is RemoteCommand.Pause -> "ssap://media.controls/pause"
            is RemoteCommand.Stop -> "ssap://media.controls/stop"
            is RemoteCommand.FastForward -> "ssap://media.controls/fastForward"
            is RemoteCommand.Rewind -> "ssap://media.controls/rewind"
            is RemoteCommand.SendText -> "ssap://com.webos.service.ime/insertText"
            is RemoteCommand.LaunchApp -> "ssap://com.webos.applicationManager/launch"
            else -> return Result.failure(IllegalArgumentException("Command not supported"))
        }

        val payload = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "request")
            put("uri", uri)
            if (command is RemoteCommand.SendText) {
                put("payload", JSONObject().apply {
                    put("text", command.text)
                    put("replace", false)
                })
            }
            if (command is RemoteCommand.LaunchApp) {
                put("payload", JSONObject().apply {
                    put("id", command.appId)
                })
            }
        }

        webSocket?.send(payload.toString())
        return Result.success(Unit)
    }

    // ... implementar disconnect e getInstalledApps similar ao Samsung
    override suspend fun disconnect() {
        webSocket?.close(1000, "Closing")
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun getInstalledApps(): Result<List<TVApp>> {
        val payload = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "request")
            put("uri", "ssap://com.webos.applicationManager/listLaunchPoints")
        }
        webSocket?.send(payload.toString())
        // Processar resposta assíncrona...
        return Result.success(emptyList())
    }
}
```

### 3.5 Camada de Apresentação (UI Jetpack Compose)

**`app/src/main/java/com/tvremote/presentation/ui/screens/RemoteControlScreen.kt`**:
```kotlin
package com.tvremote.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tvremote.core.model.RemoteCommand
import com.tvremote.presentation.viewmodel.RemoteControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showKeyboard by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.deviceName) },
                actions = {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador de status
            ConnectionStatusChip(uiState.connectionState)

            Spacer(modifier = Modifier.height(16.dp))

            // Controles de energia e volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PowerButton(onClick = { viewModel.sendCommand(RemoteCommand.PowerOff) })
                VolumeControls(
                    onVolumeUp = { viewModel.sendCommand(RemoteCommand.VolumeUp) },
                    onVolumeDown = { viewModel.sendCommand(RemoteCommand.VolumeDown) },
                    onMute = { viewModel.sendCommand(RemoteCommand.Mute) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // D-Pad de navegação
            DPad(
                onUp = { viewModel.sendCommand(RemoteCommand.Up) },
                onDown = { viewModel.sendCommand(RemoteCommand.Down) },
                onLeft = { viewModel.sendCommand(RemoteCommand.Left) },
                onRight = { viewModel.sendCommand(RemoteCommand.Right) },
                onOk = { viewModel.sendCommand(RemoteCommand.OK) },
                onBack = { viewModel.sendCommand(RemoteCommand.Back) },
                onHome = { viewModel.sendCommand(RemoteCommand.Home) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Controles de mídia
            MediaControls(
                onPlay = { viewModel.sendCommand(RemoteCommand.Play) },
                onPause = { viewModel.sendCommand(RemoteCommand.Pause) },
                onStop = { viewModel.sendCommand(RemoteCommand.Stop) },
                onFastForward = { viewModel.sendCommand(RemoteCommand.FastForward) },
                onRewind = { viewModel.sendCommand(RemoteCommand.Rewind) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botão de teclado
            OutlinedButton(
                onClick = { showKeyboard = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Teclado Virtual")
            }

            // Apps rápidos
            if (uiState.installedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                QuickAppsRow(
                    apps = uiState.installedApps.take(4),
                    onAppClick = { app ->
                        viewModel.sendCommand(RemoteCommand.LaunchApp(app.id))
                    }
                )
            }
        }

        // Dialog de teclado
        if (showKeyboard) {
            AlertDialog(
                onDismissRequest = { showKeyboard = false },
                title = { Text("Enviar Texto") },
                text = {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Digite aqui") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.sendCommand(RemoteCommand.SendText(textInput))
                            textInput = ""
                            showKeyboard = false
                        }
                    ) {
                        Text("Enviar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showKeyboard = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Botão Home
        IconButton(onClick = onHome, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Home, contentDescription = "Home")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // D-Pad
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Cima
            Button(
                onClick = onUp,
                modifier = Modifier.align(Alignment.TopCenter),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
            }

            // Baixo
            Button(
                onClick = onDown,
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
            }

            // Esquerda
            Button(
                onClick = onLeft,
                modifier = Modifier.align(Alignment.CenterStart),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Left")
            }

            // Direita
            Button(
                onClick = onRight,
                modifier = Modifier.align(Alignment.CenterEnd),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Right")
            }

            // OK
            FilledIconButton(
                onClick = onOk,
                modifier = Modifier.size(64.dp)
            ) {
                Text("OK")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Back
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
fun VolumeControls(
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onMute: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onVolumeUp) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Volume Up")
        }
        IconButton(onClick = onMute) {
            Icon(Icons.Default.VolumeOff, contentDescription = "Mute")
        }
        IconButton(onClick = onVolumeDown) {
            Icon(Icons.Default.VolumeDown, contentDescription = "Volume Down")
        }
    }
}

@Composable
fun PowerButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.size(56.dp)
    ) {
        Icon(
            Icons.Default.PowerSettingsNew,
            contentDescription = "Power",
            tint = MaterialTheme.colorScheme.onError
        )
    }
}

@Composable
fun MediaControls(
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onFastForward: () -> Unit,
    onRewind: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onRewind) {
            Icon(Icons.Default.FastRewind, contentDescription = "Rewind")
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        IconButton(onClick = onPause) {
            Icon(Icons.Default.Pause, contentDescription = "Pause")
        }
        IconButton(onClick = onStop) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
        IconButton(onClick = onFastForward) {
            Icon(Icons.Default.FastForward, contentDescription = "Fast Forward")
        }
    }
}

@Composable
fun QuickAppsRow(
    apps: List<TVApp>,
    onAppClick: (TVApp) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        apps.forEach { app ->
            AssistChip(
                onClick = { onAppClick(app) },
                label = { Text(app.name, maxLines = 1) },
                leadingIcon = {
                    // Carregar ícone async com Coil
                    AsyncImage(
                        model = app.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun ConnectionStatusChip(state: ConnectionState) {
    val (color, text) = when (state) {
        is ConnectionState.Connected -> 
            MaterialTheme.colorScheme.primary to "Conectado"
        is ConnectionState.Connecting -> 
            MaterialTheme.colorScheme.tertiary to "Conectando..."
        is ConnectionState.Error -> 
            MaterialTheme.colorScheme.error to "Erro: ${state.message}"
        is ConnectionState.Disconnected -> 
            MaterialTheme.colorScheme.outline to "Desconectado"
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
```

### 3.6 ViewModel

**`app/src/main/java/com/tvremote/presentation/viewmodel/RemoteControlViewModel.kt`**:
```kotlin
package com.tvremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.core.model.RemoteCommand
import com.tvremote.core.model.TVDevice
import com.tvremote.core.repository.TVRemoteRepository
import com.tvremote.core.repository.ConnectionState
import com.tvremote.core.repository.TVApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoteControlUiState(
    val deviceName: String = "",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val installedApps: List<TVApp> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class RemoteControlViewModel @Inject constructor(
    private val repository: TVRemoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connectionState().collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is ConnectionState.Connected) {
                    loadInstalledApps()
                }
            }
        }
    }

    fun connect(device: TVDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, deviceName = device.name) }
            repository.connect(device)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    fun sendCommand(command: RemoteCommand) {
        viewModelScope.launch {
            // Requisito RNF01: Latência < 200ms
            val startTime = System.currentTimeMillis()
            
            repository.sendCommand(command)
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            
            val latency = System.currentTimeMillis() - startTime
            if (latency > 200) {
                // Log para monitoramento de performance
                println("ALERT: Latência alta detectada: ${latency}ms")
            }
        }
    }

    private suspend fun loadInstalledApps() {
        repository.getInstalledApps()
            .onSuccess { apps ->
                _uiState.update { it.copy(installedApps = apps) }
            }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
```

---

## 4. Infraestrutura DevOps

### 4.1 GitHub Actions - CI/CD

**`.github/workflows/android-ci.yml`**:
```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  lint-and-test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Cache Gradle dependencies
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Run ktlint
      run: ./gradlew ktlintCheck --no-daemon

    - name: Run Detekt
      run: ./gradlew detekt --no-daemon

    - name: Run unit tests
      run: ./gradlew test --no-daemon

    - name: Generate test report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Unit Tests
        path: '**/build/test-results/test/*.xml'
        reporter: java-junit

  build:
    needs: lint-and-test
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Cache Gradle dependencies
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-

    - name: Build Debug APK
      run: ./gradlew assembleDebug --no-daemon

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: debug-apk
        path: app/build/outputs/apk/debug/app-debug.apk
        retention-days: 7
```

**`.github/workflows/release.yml`**:
```yaml
name: Release Build

on:
  push:
    tags:
      - 'v*'

jobs:
  build-release:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Decode Keystore
      env:
        ENCODED_STRING: ${{ secrets.KEYSTORE_BASE64 }}
      run: |
        echo $ENCODED_STRING | base64 -di > app/keystore.jks

    - name: Build Release APK
      env:
        KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
        KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
        KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      run: ./gradlew assembleRelease -Pandroid.injected.signing.store.file=keystore.jks -Pandroid.injected.signing.store.password=$KEYSTORE_PASSWORD -Pandroid.injected.signing.key.alias=$KEY_ALIAS -Pandroid.injected.signing.key.password=$KEY_PASSWORD --no-daemon

    - name: Create Release
      uses: softprops/action-gh-release@v1
      with:
        files: app/build/outputs/apk/release/app-release.apk
        generate_release_notes: true
```

---

## 5. Testes e Deploy

### 5.1 Testes Unitários

**`app/src/test/java/com/tvremote/RemoteControlViewModelTest.kt`**:
```kotlin
package com.tvremote

import app.cash.turbine.test
import com.tvremote.core.model.RemoteCommand
import com.tvremote.core.model.TVDevice
import com.tvremote.core.model.TVBrand
import com.tvremote.core.repository.ConnectionState
import com.tvremote.core.repository.TVRemoteRepository
import com.tvremote.presentation.viewmodel.RemoteControlViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlViewModelTest {

    private lateinit var viewModel: RemoteControlViewModel
    private lateinit var repository: TVRemoteRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = RemoteControlViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connect should update UI state on success`() = runTest {
        // Given
        val device = TVDevice(
            name = "Samsung TV",
            ipAddress = "192.168.1.100",
            port = 8002,
            brand = TVBrand.SAMSUNG
        )
        coEvery { repository.connect(device) } returns Result.success(Unit)
        coEvery { repository.connectionState() } returns flowOf(ConnectionState.Connected(device))

        // When
        viewModel.connect(device)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Samsung TV", state.deviceName)
            assert(state.connectionState is ConnectionState.Connected)
        }
    }

    @Test
    fun `sendCommand should complete within 200ms`() = runTest {
        // Given
        coEvery { repository.sendCommand(any()) } returns Result.success(Unit)

        // When
        val startTime = System.currentTimeMillis()
        viewModel.sendCommand(RemoteCommand.VolumeUp)
        val endTime = System.currentTimeMillis()

        // Then
        assert(endTime - startTime < 200) {
            "Command took too long: ${endTime - startTime}ms"
        }
        coVerify { repository.sendCommand(RemoteCommand.VolumeUp) }
    }
}
```

### 5.2 Testes de Integração (Espresso)

**`app/src/androidTest/java/com/tvremote/RemoteControlFlowTest.kt`**:
```kotlin
package com.tvremote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvremote.presentation.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteControlFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testNavigationFlow() {
        // Testar descoberta de dispositivos
        composeTestRule.onNodeWithText("Procurar TVs").performClick()
        
        // Aguardar lista de dispositivos
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("tv_device_item").fetchSemanticsNodes().isNotEmpty()
        }

        // Selecionar primeiro dispositivo
        composeTestRule.onAllNodesWithTag("tv_device_item")[0].performClick()

        // Verificar se controles estão visíveis
        composeTestRule.onNodeWithContentDescription("Power").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volume Up").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Up").assertIsDisplayed()
    }

    @Test
    fun testDpadInteraction() {
        // Testar cliques no D-Pad
        composeTestRule.onNodeWithContentDescription("Up").performClick()
        composeTestRule.onNodeWithContentDescription("Down").performClick()
        composeTestRule.onNodeWithContentDescription("Left").performClick()
        composeTestRule.onNodeWithContentDescription("Right").performClick()
        composeTestRule.onNodeWithContentDescription("OK").performClick()
    }
}
```

### 5.3 Scripts de Deploy

**`scripts/deploy.sh`**:
```bash
#!/bin/bash

# Script de deploy automatizado
set -e

VERSION=$1
if [ -z "$VERSION" ]; then
    echo "Uso: ./deploy.sh v1.0.0"
    exit 1
fi

echo "🚀 Iniciando deploy da versão $VERSION..."

# 1. Verificar branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "❌ Erro: Deve estar na branch main"
    exit 1
fi

# 2. Atualizar versão no build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"${VERSION#v}\"/" app/build.gradle.kts

# 3. Commit da versão
git add app/build.gradle.kts
git commit -m "chore: bump version to $VERSION"

# 4. Criar tag
git tag -a $VERSION -m "Release $VERSION"
git push origin main --tags

# 5. Build com Docker
docker-compose -f docker/docker-compose.yml up android-build

# 6. Upload para Firebase App Distribution (opcional)
if command -v firebase &> /dev/null; then
    firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
        --app 1:1234567890:android:abc123 \
        --groups "testers"
fi

echo "✅ Deploy concluído!"
```

---

## 📱 Fluxo de Desenvolvimento Recomendado

### Para CachyOS + Galaxy S21 FE + POCO Pad:

1. **Setup inicial**:
   ```bash
   # Instalar Android Studio via Flatpak ou JetBrains Toolbox
   flatpak install flathub com.google.AndroidStudio
   
   # Configurar ADB para dispositivos USB
   sudo pacman -S android-tools
   ```

2. **Desenvolvimento com Hot Reload**:
   - Usar **Wireless Debugging** para Galaxy S21 FE (Android 14+)
   - Pair via QR code: `adb pair IP:PORT`
   - Conectar: `adb connect IP:PORT`

3. **Testes no POCO Pad**:
   - Otimizar layouts para tablet usando `WindowSizeClass`
   - Testar modo paisagem (landscape) com controles divididos

4. **Pipeline de qualidade**:
   ```bash
   # Antes de cada commit
   ./gradlew ktlintFormat detekt
   
   # Testes locais
   ./gradlew testDebugUnitTest
   
   # Build de release
   ./gradlew assembleRelease
   ```

---

## 🔄 Checklist de Implementação

| Fase | Tarefa | Status |
|------|--------|--------|
| **Setup** | Repositório Git com Git Flow | ⬜ |
| | Docker configurado | ⬜ |
| | CI/CD no GitHub Actions | ⬜ |
| **Core** | Modelos de dados | ⬜ |
| | Contratos de repositório | ⬜ |
| | Injeção de dependência (Hilt) | ⬜ |
| **Discovery** | SSDP/mDNS implementation | ⬜ |
| | UI de listagem de dispositivos | ⬜ |
| **Samsung** | WebSocket Tizen API | ⬜ |
| | Pareamento com PIN | ⬜ |
| | Comandos básicos | ⬜ |
| **LG** | WebOS WebSocket | ⬜ |
| | Pareamento com client-key | ⬜ |
| | Magic Remote (mouse) | ⬜ |
| **Roku** | ECP REST API | ⬜ |
| | Deep linking apps | ⬜ |
| **Sony** | Bravia REST API | ⬜ |
| | PSK authentication | ⬜ |
| **UI/UX** | Layout responsivo (phone/tablet) | ⬜ |
| | Animações de feedback tátil | ⬜ |
| | Dark/Light theme | ⬜ |
| **Testes** | Unit tests > 80% coverage | ⬜ |
| | Integration tests | ⬜ |
| | Performance tests (<200ms) | ⬜ |
| **Deploy** | Google Play Console setup | ⬜ |
| | Firebase Crashlytics | ⬜ |
| | Analytics | ⬜ |

---

## 📚 Recursos Adicionais

- **Samsung Tizen API**: https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references
- **LG WebOS**: https://www.webosose.org/docs/reference/ls2-api/com-webos-service-ime/
- **Roku ECP**: https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
- **Sony Bravia**: https://pro-bravia.sony.net/develop/index.html

Este projeto é complexo devido às diferentes APIs de cada fabricante. Recomendo começar implementando **uma marca primeiro** (sugiro Samsung por ter a documentação mais acessível) e depois expandir para as demais usando o padrão Strategy/Factory para os repositórios.