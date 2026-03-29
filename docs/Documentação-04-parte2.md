

# 🏗️ ETAPA 2: Arquitetura e Comunicação de TVs
Nesta etapa, implementaremos a espinha dorsal do aplicativo. Utilizaremos **MVVM + Clean Architecture** para isolar a UI da lógica de rede. Depois, construiremos os serviços que descobrem TVs na rede Wi-Fi (usando mDNS) e as classes (Connectors) que sabem conversar com LG, Samsung e Roku.

## 7. Arquitetura MVVM + Clean Architecture

O app segue **MVVM** com **Clean Architecture** em 3 camadas:

```
┌─────────────────────────────────────┐
│          Presentation Layer          │
│  (Composables + ViewModels + State)  │
└──────────────┬──────────────────────┘
               │ usa
┌──────────────▼──────────────────────┐
│            Domain Layer              │
│  (Interfaces de Repositório +        │
│   Modelos de Domínio)                │
└──────────────┬──────────────────────┘
               │ implementado por
┌──────────────▼──────────────────────┐
│             Data Layer               │
│  (Room + WebSocket + HTTP +          │
│   NsdManager / mDNS)                 │
└─────────────────────────────────────┘
```

### Fluxo de dados (unidirecional):

```
UI (Composable)
    ↓ evento (click)
ViewModel
    ↓ chama
Repository (interface do domain)
    ↓ implementado por
DataSource (WebSocket/HTTP/Room)
    ↓ retorna via Flow/Result
ViewModel
    ↓ atualiza StateFlow
UI (recomposição)
```

---

## 8. Módulo Core — Modelos e Contratos [✅]

### 8.1 Criar classe Application com Hilt [✅]

Crie `app/src/main/java/com/antoniosilva/universaltvremote/UniversalTVRemoteApp.kt`:

```kotlin
package com.antoniosilva.universaltvremote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UniversalTVRemoteApp : Application()
```

### 8.2 Modelo de dispositivo TV [✅]

#### Atenção:
Todo e qualquer arquivo Kotlin que você criar a partir de agora deverá ser posicionado a partir deste diretório absoluto:

`app/src/main/java/com/antoniosilva/universaltvremote/`


Crie `domain/model/TvDevice.kt`:

```kotlin
package com.antoniosilva.universaltvremote.domain.model

data class TvDevice(
    val id: String,              // MAC address ou UUID único
    val name: String,            // Ex: "Samsung QLED 55"
    val brand: TvBrand,
    val ipAddress: String,
    val port: Int,               // 8001/8002 Samsung, 3000 LG, 8060 Roku
    val model: String? = null,
    val isPaired: Boolean = false,
    
    // 🛡️ SEGURANÇA: Em produção, tokens e chaves não devem ser salvos em texto plano (SharedPreferences ou Room).
    // Recomenda-se o uso de 'androidx.security:security-crypto' para persistência cifrada de dados sensíveis.
    // Para fins didáticos, este projeto foca na Clean Architecture e Hilt; a persistência será feita 
    // via Room Database (Seção 11 da documentação) de forma simples, deixando a criptografia como uma melhoria futura.
    val authToken: String? = null
)

enum class TvBrand {
    SAMSUNG,
    LG,
    ROKU,
    SONY,
    ANDROID_TV,
    UNKNOWN
}
```

### 8.3 Sealed class de comandos [✅]

Crie `domain/model/TvCommand.kt`:

```kotlin
package com.antoniosilva.universaltvremote.domain.model

sealed class TvCommand {
    // Energia
    object PowerOn : TvCommand()
    object PowerOff : TvCommand()

    // Volume
    object VolumeUp : TvCommand()
    object VolumeDown : TvCommand()
    object Mute : TvCommand()

    // Canais
    object ChannelUp : TvCommand()
    object ChannelDown : TvCommand()
    data class ChannelNumber(val number: Int) : TvCommand()

    // Navegação D-Pad
    object Up : TvCommand()
    object Down : TvCommand()
    object Left : TvCommand()
    object Right : TvCommand()
    object Ok : TvCommand()
    object Back : TvCommand()
    object Home : TvCommand()
    object Menu : TvCommand()

    // Mídia
    object Play : TvCommand()
    object Pause : TvCommand()
    object Stop : TvCommand()
    object FastForward : TvCommand()
    object Rewind : TvCommand()

    // Entrada de texto
    data class SendText(val text: String) : TvCommand()

    // Abrir / Iniciar aplicativo que já está instalado na TV, como Netflix, Youtube, Prime Video, etc.
    data class LaunchApp(val appId: String) : TvCommand()
}
```

### 8.4 Estado de conexão [✅]

Crie `domain/model/ConnectionState.kt`:

```kotlin
package com.antoniosilva.universaltvremote.domain.model

/**
 * Sealed Class ConnectionState (Estado da Conexão)
 * Representa todas as situações possíveis entre o Celular e a TV.
 * Por ser 'sealed', o compilador garante que trataremos todos os casos na UI.
 */
sealed class ConnectionState {

    // Estado atômico (object): Indica que o app não está tentando se comunicar com nenhuma TV.
    // Usado como estado inicial ou após o usuário clicar em "Desconectar".
    object Disconnected : ConnectionState()

    // Estado atômico (object): Indica que o app iniciou o 'handshake' com a TV.
    // Usado para exibir o Spinner (ícone de carregamento) na tela.
    object Connecting : ConnectionState()

    // Estado com dados (data class): Indica sucesso na conexão.
    // Carrega o objeto 'TvDevice' para que a UI saiba o nome e o IP da TV conectada.
    data class Connected(val device: TvDevice) : ConnectionState()

    // Estado com dados (data class): Indica que algo deu errado (Ex: Wi-Fi caiu ou senha errada).
    // Carrega a 'message' técnica para ser exibida em um alerta ou Snackbar para o usuário.
    data class Error(val message: String) : ConnectionState()
}
```

### 8.5 Interface do repositório (contrato) [✅]

Crie `domain/repository/TvRemoteRepository.kt`:

```kotlin
package com.antoniosilva.universaltvremote.domain.repository

import com.antoniosilva.universaltvremote.domain.model.ConnectionState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow

/**
 * Interface TvRemoteRepository (Contrato de Domínio)
 * Define O QUE o sistema faz, sem se preocupar com o COMO (Samsung, LG ou Roku).
 */
interface TvRemoteRepository {

    // Retorna um Fluxo Reativo (Flow) que emite mudanças de estado da conexão em tempo real.
    // Permite que a UI "escute" se a TV desconectou ou se houve erro sem travar o app.
    fun connectionState(): Flow<ConnectionState>

    // Função Suspensa: Inicia o processo de conexão e autenticação com a TV.
    // O 'Result<Unit>' indica sucesso ou falha na tentativa de conexão.
    suspend fun connect(device: TvDevice): Result<Unit>

    // Encerra a conexão WebSocket ou HTTP ativa com o dispositivo.
    suspend fun disconnect()

    // Envia um comando (Power, Volume, etc.) para a TV.
    // É 'suspend' porque a rede pode demorar a responder.
    suspend fun sendCommand(command: TvCommand): Result<Unit>

    // Busca a lista de aplicativos instalados na TV (Netflix, YouTube, etc.).
    // Retorna uma lista de objetos TvApp encapsulada em um Result.
    suspend fun getInstalledApps(): Result<List<TvApp>>
}

/**
 * Modelo de dados para representar um aplicativo dentro da Smart TV.
 */
data class TvApp(
    val id: String,      // Identificador único do app na TV (ex: "youtube")
    val name: String,    // Nome exibido ao usuário
    val iconUrl: String? = null // Link opcional para o ícone do aplicativo
)
```
## 9. Descoberta de Dispositivos na Rede

O app usa **NsdManager** (API nativa Android) para descoberta via mDNS, sem biblioteca externa.

### 9.1 Serviço de descoberta


Crie `data/remote/discovery/TvDiscoveryService.kt`
Caminho absoluto: `app/src/main/java/com/antoniosilva/universaltvremote/data/remote/discovery/TvDiscoveryService.kt`

```kotlin
package com.antoniosilva.universaltvremote.data.remote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.antoniosilva.universaltvremote.domain.model.TvBrand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TvDiscovery"

// Tipos de serviço mDNS por fabricante
private val SERVICE_TYPES = mapOf(
    "_samsung-remote._tcp" to TvBrand.SAMSUNG,
    "_lg-smart-tv._tcp"   to TvBrand.LG,
    "_roku-ecp._tcp"      to TvBrand.ROKU,
    "_sony-rc._tcp"       to TvBrand.SONY
)

@Singleton
class TvDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Executor dedicado para callbacks do NsdManager (API 33+)
    private val resolveExecutor = Executors.newSingleThreadExecutor()

    /**
     * Emite [TvDevice] conforme dispositivos são descobertos na rede local.
     * Encerra automaticamente quando o Flow é cancelado.
     */
    fun discoverDevices(): Flow<TvDevice> = callbackFlow {
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        SERVICE_TYPES.forEach { (serviceType, brand) ->
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery start failed: $serviceType, error: $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery stop failed: $serviceType, error: $errorCode")
                }
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "Discovery started: $serviceType")
                }
                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "Discovery stopped: $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveService(serviceInfo, brand)
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                }
            }

            listeners.add(discoveryListener)
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        }

        // Parar descoberta quando o Flow for cancelado
        awaitClose {
            listeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            // ⚡ PERFORMANCE / BUG FIX: Não faça resolveExecutor.shutdown() aqui!
            // Como este Service é um @Singleton (injetado via Hilt), se você desligar as threads do executor, 
            // a próxima vez que o usuário iniciar a busca a aplicação sofrerá um RejectedExecutionException 
            // resultando num CRASH instantâneo.
        }
    }

    /**
     * Resolve o IP e porta de um serviço descoberto.
     *
     * COMPATÍBILIDADE:
     *   - API 33+ (Android 13+): usa a API com Executor, que é thread-safe
     *     e não sofre do bug de "already active" presente na API legada.
     *   - API < 33 (Android 8–12): usa a API com ResolveListener (depreciada
     *     a partir da API 33, mas ainda funcional em versões anteriores).
     *
     * FUTURO — Android 16 (API 36):
     *   A API com Executor é a forma recomendada para API 36+.
     *   Não é necessário alterar este código ao migrar para targetSdk = 36.
     */
    private fun <T> kotlinx.coroutines.channels.ProducerScope<T>.resolveService(
        serviceInfo: NsdServiceInfo,
        brand: TvBrand
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+): API moderna com Executor
            // Evita o bug "already active" da API legada e é thread-safe.
            nsdManager.resolveService(
                serviceInfo,
                resolveExecutor,
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        Log.e(TAG, "Resolve registration failed: $errorCode")
                    }
                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        buildAndSendDevice(resolvedInfo, brand)
                    }
                    override fun onServiceLost() {
                        Log.d(TAG, "Service lost during resolve")
                    }
                    override fun onServiceInfoCallbackUnregistered() {
                        Log.d(TAG, "Resolve callback unregistered")
                    }
                }
            )
        } else {
            // Android 8–12 (API 26–32): API legada com ResolveListener
            // Depreciada na API 33, mas necessária para manter minSdk = 26.
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Resolve failed: $errorCode")
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    buildAndSendDevice(serviceInfo, brand)
                }
            })
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> kotlinx.coroutines.channels.ProducerScope<T>.buildAndSendDevice(
        serviceInfo: NsdServiceInfo,
        brand: TvBrand
    ) {
        val device = TvDevice(
            id = serviceInfo.host?.hardwareAddress
                ?.joinToString(":") { "%02X".format(it) }
                ?: UUID.randomUUID().toString(),
            name = serviceInfo.serviceName,
            brand = brand,
            ipAddress = serviceInfo.host?.hostAddress ?: return,
            port = serviceInfo.port
        )
        trySend(device as T)
    }
}
```

### 9.2 ViewModel de descoberta

Crie `presentation/viewmodel/DiscoveryViewModel.kt`:

```kotlin
package com.antoniosilva.universaltvremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoniosilva.universaltvremote.data.remote.discovery.TvDiscoveryService
import com.antoniosilva.universaltvremote.data.local.PairedTvRepository
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveryUiState(
    val discoveredDevices: List<TvDevice> = emptyList(),
    val pairedDevices: List<TvDevice> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryService: TvDiscoveryService,
    private val pairedTvRepository: PairedTvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var discoveryJob: kotlinx.coroutines.Job? = null

    init {
        // Carregar TVs pareadas do banco local assim que o ViewModel é criado
        viewModelScope.launch {
            pairedTvRepository.getPairedTvs().collect { paired ->
                _uiState.update { it.copy(pairedDevices = paired) }
            }
        }
    // Inicia a busca contínua por TVs
    fun startScan() {
        discoveryJob?.cancel() // Cancela busca anterior (se houver) para evitar vazamento de memória
        _uiState.update { it.copy(isScanning = true, discoveredDevices = emptyList()) }

        discoveryJob = viewModelScope.launch { // Coroutine atrelada ao ciclo de vida da tela
            discoveryService.discoverDevices()
                .catch { e -> _uiState.update { it.copy(error = e.message, isScanning = false) } }
                .collect { device ->
                    _uiState.update { state ->
                        val current = state.discoveredDevices.toMutableList()
                        // Evita duplicar a mesma TV na lista se o mDNS disparar vários achados pro mesmo IP
                        if (current.none { it.ipAddress == device.ipAddress }) {
                            current.add(device)
                        }
                        state.copy(discoveredDevices = current)
                    }
                }
        }
    }

    fun stopScan() {
        discoveryJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }

    override fun onCleared() {
        super.onCleared()
        // Garante que a busca da TV encerre se o App for pro background/Fechado
        discoveryJob?.cancel()
    }
}
```

---

## 10. Emparelhamento e Conexão por Marca

### 10.1 Interface do conector (Strategy Pattern)

Crie `domain/repository/TvConnector.kt`:

```kotlin
package com.antoniosilva.universaltvremote.domain.repository

import com.antoniosilva.universaltvremote.domain.model.ConnectionState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow

interface TvConnector {
    fun connectionState(): Flow<ConnectionState>
    suspend fun connect(device: TvDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun sendCommand(command: TvCommand): Result<Unit>
}
```

### 10.2 Conector Samsung (Tizen — WebSocket)

Crie `data/remote/samsung/SamsungConnector.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.remote.samsung

import android.util.Base64
import com.antoniosilva.universaltvremote.domain.model.ConnectionState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import com.antoniosilva.universaltvremote.domain.repository.TvConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SamsungConnector @Inject constructor() : TvConnector {

    private var webSocket: WebSocket? = null
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    // Nome do app codificado em Base64 (exigido pela API Samsung)
    private val appName = Base64.encodeToString(
        "Universal TV Remote".toByteArray(),
        Base64.DEFAULT
    ).trim()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // sem timeout para WebSocket
        // ⚡ PERFORMANCE / ESTABILIDADE: O pingInterval mantém o WebSocket vivo por trás dos panos (evita que a TV corte a conexão)
        .pingInterval(30, TimeUnit.SECONDS)
        // 🛡️ SEGURANÇA VITAL: Em produção num canal HTTPS/WSS (SAMSUNG usa 8002 WSS também), você DEVE 
        // usar SSL Pinning (CertificatePinner) ou configurar o TrustManager para não aceitar certificados adulterados MITM.
        .build()

    override fun connectionState(): Flow<ConnectionState> = _state

    override suspend fun connect(device: TvDevice): Result<Unit> {
        _state.value = ConnectionState.Connecting
        return try {
            // Samsung usa porta 8001 (HTTP) ou 8002 (HTTPS/WSS)
            // Tentamos 8001 primeiro (mais compatível)
            val wsUrl = "ws://${device.ipAddress}:8001/api/v2/channels/samsung.remote.control" +
                    "?name=$appName" +
                    (device.authToken?.let { "&token=$it" } ?: "")

            val request = Request.Builder().url(wsUrl).build()
            webSocket = client.newWebSocket(request, SamsungWebSocketListener(device, _state))
            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = ConnectionState.Error(e.message ?: "Erro desconhecido")
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "Usuário desconectou")
        webSocket = null
        _state.value = ConnectionState.Disconnected
    }

    // Traduz o clique de botão no Compose para o payload de JSON da API da Samsung
    override suspend fun sendCommand(command: TvCommand): Result<Unit> {
        val keyCode = command.toSamsungKeyCode() ?: return Result.failure(
            IllegalArgumentException("Comando não suportado pela Samsung: $command")
        )
        
        // Estrutura obrigatória pela documentação Tizen
        val payload = JSONObject().apply {
            put("method", "ms.remote.control") # Dispara uma ação
            put("params", JSONObject().apply {
                put("Cmd", "Click") # Aperto de tecla remoto
                put("DataOfCmd", keyCode) # O botão (ex: KEY_VOLUP)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }

        webSocket?.send(payload.toString())
            ?: return Result.failure(IllegalStateException("WebSocket não conectado - ligue a TV"))
        return Result.success(Unit)
    }

    private fun TvCommand.toSamsungKeyCode(): String? = when (this) {
        is TvCommand.PowerOn      -> "KEY_POWER"
        is TvCommand.PowerOff     -> "KEY_POWER"
        is TvCommand.VolumeUp     -> "KEY_VOLUP"
        is TvCommand.VolumeDown   -> "KEY_VOLDOWN"
        is TvCommand.Mute         -> "KEY_MUTE"
        is TvCommand.ChannelUp    -> "KEY_CHUP"
        is TvCommand.ChannelDown  -> "KEY_CHDOWN"
        is TvCommand.Up           -> "KEY_UP"
        is TvCommand.Down         -> "KEY_DOWN"
        is TvCommand.Left         -> "KEY_LEFT"
        is TvCommand.Right        -> "KEY_RIGHT"
        is TvCommand.Ok           -> "KEY_ENTER"
        is TvCommand.Back         -> "KEY_RETURN"
        is TvCommand.Home         -> "KEY_HOME"
        is TvCommand.Menu         -> "KEY_MENU"
        is TvCommand.Play         -> "KEY_PLAY"
        is TvCommand.Pause        -> "KEY_PAUSE"
        is TvCommand.Stop         -> "KEY_STOP"
        is TvCommand.FastForward  -> "KEY_FF"
        is TvCommand.Rewind       -> "KEY_REWIND"
        is TvCommand.ChannelNumber -> "KEY_${number}"
        else -> null
    }
}

private class SamsungWebSocketListener(
    private val device: TvDevice,
    private val state: MutableStateFlow<ConnectionState>
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        state.value = ConnectionState.Connected(device)
    }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        state.value = ConnectionState.Error(t.message ?: "Falha na conexão")
    }
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        state.value = ConnectionState.Disconnected
    }
    override fun onMessage(webSocket: WebSocket, text: String) {
        // Processar mensagens da TV (ex.: token de pareamento)
        runCatching {
            val json = JSONObject(text)
            val token = json.optJSONObject("data")?.optString("token")
            if (!token.isNullOrBlank()) {
                // 🛡️ SEGURANÇA / VAZAMENTO: NUNCA use Log.d ou Println para imprimir tokens confidenciais.
                // Outros aplicativos maliciosos lendo o Logcat podem sequestrar a comunicação da TV.
                // Aqui você deve interceptar local e guardar com EncryptedSharedPreferences (ver seção 11 na Documentação-04-parte2.md).
            }
        }
    }
}
```

### 10.3 Conector LG (WebOS — WebSocket porta 3000)

Crie `data/remote/lg/LGConnector.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.remote.lg

import com.antoniosilva.universaltvremote.domain.model.ConnectionState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import com.antoniosilva.universaltvremote.domain.repository.TvConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class LGConnector @Inject constructor() : TvConnector {

    private var webSocket: WebSocket? = null
    private var clientKey: String? = null
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val client = OkHttpClient()
    private lateinit var currentDevice: TvDevice

    override fun connectionState(): Flow<ConnectionState> = _state

    override suspend fun connect(device: TvDevice): Result<Unit> {
        currentDevice = device
        clientKey = device.authToken  // reutilizar client-key salvo
        _state.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url("ws://${device.ipAddress}:3000/")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Registrar app na TV LG WebOS
                val registerPayload = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("type", "register")
                    put("payload", JSONObject().apply {
                        put("forcePairing", false)
                        put("pairingType", "PROMPT") // Exibe prompt na TV
                        put("client-key", clientKey) // null na primeira conexão
                        put("manifest", JSONObject().apply {
                            put("manifestVersion", 1)
                            put("appVersion", "1.0")
                            put("permissions", listOf(
                                "CONTROL_AUDIO",
                                "CONTROL_POWER",
                                "CONTROL_INPUT_MEDIA_PLAYBACK",
                                "LAUNCH",
                                "READ_INSTALLED_APPS",
                                "READ_RUNNING_APPS",
                                "READ_CURRENT_CHANNEL",
                                "READ_TV_CHANNEL_LIST"
                            ))
                        })
                    })
                }
                webSocket.send(registerPayload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val response = JSONObject(text)
                when (response.optString("type")) {
                    "registered" -> {
                        // Salvar client-key para futuras conexões sem prompt
                        clientKey = response.getJSONObject("payload").optString("client-key")
                        _state.value = ConnectionState.Connected(device)
                    }
                    "error" -> {
                        val errorText = response.optString("error", "Erro desconhecido")
                        _state.value = ConnectionState.Error(errorText)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.Error(t.message ?: "Falha na conexão LG")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.Disconnected
            }
        })
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "Desconectando")
        webSocket = null
        _state.value = ConnectionState.Disconnected
    }

    // ⚠️ Correção da Doc-01: comandos de navegação LG usam
    // "ssap://com.webos.service.ime/sendEnterKey" APENAS para OK.
    // Para direcionais, use "ssap://com.webos.service.ime/sendCursorVisibility"
    // ou o botão virtual via INPUT_CONTROL.
    override suspend fun sendCommand(command: TvCommand): Result<Unit> {
        val uri = when (command) {
            is TvCommand.PowerOff     -> "ssap://system/turnOff"
            is TvCommand.VolumeUp     -> "ssap://audio/volumeUp"
            is TvCommand.VolumeDown   -> "ssap://audio/volumeDown"
            is TvCommand.Mute         -> "ssap://audio/setMute"
            is TvCommand.Play         -> "ssap://media.controls/play"
            is TvCommand.Pause        -> "ssap://media.controls/pause"
            is TvCommand.Stop         -> "ssap://media.controls/stop"
            is TvCommand.FastForward  -> "ssap://media.controls/fastForward"
            is TvCommand.Rewind       -> "ssap://media.controls/rewind"
            is TvCommand.LaunchApp    -> "ssap://com.webos.applicationManager/launch"
            is TvCommand.SendText     -> "ssap://com.webos.service.ime/insertText"
            is TvCommand.Home         -> "ssap://com.webos.applicationManager/launch"
            else -> null
        }

        uri ?: return Result.failure(IllegalArgumentException("Comando não suportado: $command"))

        val payload = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "request")
            put("uri", uri)
            when (command) {
                is TvCommand.SendText -> put("payload", JSONObject().apply {
                    put("text", command.text); put("replace", false)
                })
                is TvCommand.LaunchApp -> put("payload", JSONObject().apply {
                    put("id", command.appId)
                })
                else -> {}
            }
        }

        webSocket?.send(payload.toString())
            ?: return Result.failure(IllegalStateException("Não conectado"))
        return Result.success(Unit)
    }

    fun getClientKey(): String? = clientKey
}
```

### 10.4 Conector Roku (ECP — HTTP REST, sem pareamento)

Crie `data/remote/roku/RokuConnector.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.remote.roku

import com.antoniosilva.universaltvremote.domain.model.ConnectionState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import com.antoniosilva.universaltvremote.domain.repository.TvConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class RokuConnector @Inject constructor(
    private val client: OkHttpClient
) : TvConnector {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private var baseUrl: String = ""

    override fun connectionState(): Flow<ConnectionState> = _state

    // Roku não precisa de pareamento: basta guardar o IP
    override suspend fun connect(device: TvDevice): Result<Unit> {
        baseUrl = "http://${device.ipAddress}:8060"
        _state.value = ConnectionState.Connected(device)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
    }

    // ⚡ PERFORMANCE / CARGA (Gargalo de Thread): 
    // Funções suspend no Kotlin NÃO deveriam bloquear a thread chamadora. 
    // O OkHttp 'execute()' é estritamente síncrono. Inserimos num contexto IO 
    // para assegurar que ele rode num pool paralelo não bloqueando a Main UI Thread.
    override suspend fun sendCommand(command: TvCommand): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = command.toRokuKey()
            ?: return@withContext Result.failure(IllegalArgumentException("Comando não suportado: $command"))

        val url = "$baseUrl/keypress/$key"
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun TvCommand.toRokuKey(): String? = when (this) {
        is TvCommand.PowerOn      -> "Power"
        is TvCommand.PowerOff     -> "PowerOff"
        is TvCommand.VolumeUp     -> "VolumeUp"
        is TvCommand.VolumeDown   -> "VolumeDown"
        is TvCommand.Mute         -> "VolumeMute"
        is TvCommand.ChannelUp    -> "ChannelUp"
        is TvCommand.ChannelDown  -> "ChannelDown"
        is TvCommand.Up           -> "Up"
        is TvCommand.Down         -> "Down"
        is TvCommand.Left         -> "Left"
        is TvCommand.Right        -> "Right"
        is TvCommand.Ok           -> "Select"
        is TvCommand.Back         -> "Back"
        is TvCommand.Home         -> "Home"
        is TvCommand.Play         -> "Play"
        is TvCommand.FastForward  -> "Fwd"
        is TvCommand.Rewind       -> "Rev"
        else -> null
    }
}
```

### 10.5 Factory para obter o conector correto

Crie `data/remote/TvConnectorFactory.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.remote

import com.antoniosilva.universaltvremote.data.remote.lg.LGConnector
import com.antoniosilva.universaltvremote.data.remote.roku.RokuConnector
import com.antoniosilva.universaltvremote.data.remote.samsung.SamsungConnector
import com.antoniosilva.universaltvremote.domain.model.TvBrand
import com.antoniosilva.universaltvremote.domain.repository.TvConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvConnectorFactory @Inject constructor(
    private val samsungConnector: SamsungConnector,
    private val lgConnector: LGConnector,
    private val rokuConnector: RokuConnector
) {
    fun getConnector(brand: TvBrand): TvConnector = when (brand) {
        TvBrand.SAMSUNG     -> samsungConnector
        TvBrand.LG          -> lgConnector
        TvBrand.ROKU        -> rokuConnector
        TvBrand.SONY        -> samsungConnector // Sony Bravia usa protocolo similar ao Samsung
        TvBrand.ANDROID_TV  -> rokuConnector    // Implementar GoogleTvConnector futuramente
        TvBrand.UNKNOWN     -> rokuConnector    // Fallback HTTP
    }
}
```

---

## 11. Persistência de Dados com Room

### 11.1 Entidade do banco de dados

Crie `data/local/entity/PairedTvEntity.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paired_tvs")
data class PairedTvEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String,          // TvBrand.name()
    val ipAddress: String,
    val port: Int,
    val authToken: String?,     // token Samsung ou client-key LG
    val lastUsed: Long = System.currentTimeMillis()
)
```

### 11.2 DAO

Crie `data/local/dao/PairedTvDao.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.local.dao

import androidx.room.*
import com.antoniosilva.universaltvremote.data.local.entity.PairedTvEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedTvDao {
    @Query("SELECT * FROM paired_tvs ORDER BY lastUsed DESC")
    fun getAll(): Flow<List<PairedTvEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tv: PairedTvEntity)

    @Delete
    suspend fun delete(tv: PairedTvEntity)

    @Query("SELECT * FROM paired_tvs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PairedTvEntity?
}
```

### 11.3 Banco de dados Room

Crie `data/local/AppDatabase.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.antoniosilva.universaltvremote.data.local.dao.PairedTvDao
import com.antoniosilva.universaltvremote.data.local.entity.PairedTvEntity

@Database(
    entities = [PairedTvEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pairedTvDao(): PairedTvDao
}
```

### 11.4 Repositório de TVs Pareadas

Crie `data/local/PairedTvRepository.kt`:

```kotlin
package com.antoniosilva.universaltvremote.data.local

import com.antoniosilva.universaltvremote.data.local.dao.PairedTvDao
import com.antoniosilva.universaltvremote.data.local.entity.PairedTvEntity
import com.antoniosilva.universaltvremote.domain.model.TvBrand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairedTvRepository @Inject constructor(
    private val dao: PairedTvDao
) {
    fun getPairedTvs(): Flow<List<TvDevice>> =
        dao.getAll().map { list -> list.map { it.toTvDevice() } }

    suspend fun savePairedTv(device: TvDevice) =
        dao.upsert(device.toEntity())

    suspend fun removePairedTv(device: TvDevice) =
        dao.delete(device.toEntity())

    private fun PairedTvEntity.toTvDevice() = TvDevice(
        id = id, name = name,
        brand = runCatching { TvBrand.valueOf(brand) }.getOrDefault(TvBrand.UNKNOWN),
        ipAddress = ipAddress, port = port, authToken = authToken, isPaired = true
    )

    private fun TvDevice.toEntity() = PairedTvEntity(
        id = id, name = name, brand = brand.name,
        ipAddress = ipAddress, port = port, authToken = authToken
    )
}
```

### 🔁 **O Ciclo do Git Flow (Camada de Dados)**

Pronto! Você terminou a espinha dorsal de comunicação (Descoberta, Conectores e Banco de Dados Local). Vamos salvar esse progresso na nossa branch.

```bash
# 1. Adicione a pasta "data" à área (staging)
git add app/src/main/java/com/antoniosilva/universaltvremote/data/

# 2. Registre as alterações
git commit -m "feat(data): implementar conectores de TV (Samsung, LG, Roku), mDNS e Room DB"

# 3. Envie a branch atual para backup e histórico no GitHub
git push -u origin feature/core-network
```

---

## 12. Interface do Usuário com Jetpack Compose

### 12.1 MainActivity

Crie `presentation/MainActivity.kt`:

```kotlin
package com.antoniosilva.universaltvremote.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// enableEdgeToEdge(): OBRIGATÓRIO para Android 15 (targetSdk = 35).
// Faz o app renderizar atrás das barras de sistema (status bar e nav bar).
// Requer activity-compose 1.10+ e core-ktx 1.16+.
// O Scaffold já trata o padding interno automaticamente com WindowInsets.
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.antoniosilva.universaltvremote.presentation.navigation.AppNavigation
import com.antoniosilva.universaltvremote.presentation.theme.UniversalTVRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15 (API 35): enableEdgeToEdge() deve ser chamado ANTES de
        // setContent{} e ANTES de super.onCreate() se estiver customizando
        // a cor das barras de sistema.
        //
        // Com targetSdk = 35, o sistema já faz edge-to-edge por padrão, mas
        // chamar enableEdgeToEdge() aqui garante o comportamento correto em
        // todas as versões (API 26+), inclusive controlando as cores das barras.
        //
        // FUTURO — Android 16 (targetSdk = 36):
        //   Nenhuma mudança adicional é necessária aqui.
        //   O Android 16 adiciona Predictive Back Gestures por padrão;
        //   caso queira personalizar a animação de "voltar", implemente
        //   OnBackPressedCallback com a animação preditiva (opcional).
        enableEdgeToEdge()

        setContent {
            UniversalTVRemoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
```

### 12.2 Navegação

Crie `presentation/navigation/AppNavigation.kt`:

```kotlin
package com.antoniosilva.universaltvremote.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.antoniosilva.universaltvremote.presentation.screens.DiscoveryScreen
import com.antoniosilva.universaltvremote.presentation.screens.RemoteControlScreen

sealed class Screen(val route: String) {
    object Discovery : Screen("discovery")
    object RemoteControl : Screen("remote/{deviceId}") {
        fun createRoute(deviceId: String) = "remote/$deviceId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Discovery.route) {
        composable(Screen.Discovery.route) {
            DiscoveryScreen(
                onDeviceSelected = { device ->
                    navController.navigate(Screen.RemoteControl.createRoute(device.id))
                }
            )
        }
        composable(
            route = Screen.RemoteControl.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) {
            RemoteControlScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

### 12.3 Tela de Descoberta

Crie `presentation/screens/DiscoveryScreen.kt`:

```kotlin
package com.antoniosilva.universaltvremote.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import com.antoniosilva.universaltvremote.presentation.viewmodel.DiscoveryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onDeviceSelected: (TvDevice) -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal TV Remote") },
                actions = {
                    IconButton(onClick = {
                        if (state.isScanning) viewModel.stopScan()
                        else viewModel.startScan()
                    }) {
                        if (state.isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Buscar TVs")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (state.pairedDevices.isNotEmpty()) {
                Text(
                    "TVs Salvas",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                TvDeviceList(devices = state.pairedDevices, onSelect = onDeviceSelected)
                HorizontalDivider()
            }

            Text(
                if (state.isScanning) "Buscando TVs na rede..." else "TVs Encontradas",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.discoveredDevices.isEmpty() && !state.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Tv, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhuma TV encontrada")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.startScan() }) {
                            Text("Buscar TVs")
                        }
                    }
                }
            } else {
                TvDeviceList(devices = state.discoveredDevices, onSelect = onDeviceSelected)
            }
        }
    }
}

@Composable
fun TvDeviceList(devices: List<TvDevice>, onSelect: (TvDevice) -> Unit) {
    LazyColumn {
        items(devices, key = { it.id }) { device ->
            ListItem(
                headlineContent = { Text(device.name) },
                supportingContent = { Text("${device.brand.name} • ${device.ipAddress}:${device.port}") },
                leadingContent = { Icon(Icons.Default.Tv, contentDescription = null) },
                trailingContent = {
                    if (device.isPaired) Badge { Text("Salvo") }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(device) }
                    .testTag("tv_device_item")
            )
            HorizontalDivider()
        }
    }
}
```

### 12.4 Tela de Controle Remoto

Crie `presentation/screens/RemoteControlScreen.kt`:

```kotlin
package com.antoniosilva.universaltvremote.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.presentation.viewmodel.RemoteControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showKeyboard by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deviceName.ifEmpty { "Controle Remoto" }) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.disconnect(); onBack() }) {
                        // Android 15+: Icons.AutoMirrored.Filled.ArrowBack é a forma
                        // correta para ícones que devem ser espelhados em layouts RTL.
                        // Icons.Default.ArrowBack foi depreciado.
                        // Adicione o import:
                        // import androidx.compose.material.icons.automirrored.filled.ArrowBack
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Botão de energia
            FilledIconButton(
                onClick = { viewModel.send(TvCommand.PowerOff) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power",
                    tint = MaterialTheme.colorScheme.onError)
            }

            Spacer(Modifier.height(24.dp))

            // Volume e Canal lado a lado
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Volume
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VOL", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { viewModel.send(TvCommand.VolumeUp) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Volume +")
                    }
                    IconButton(onClick = { viewModel.send(TvCommand.Mute) }) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Mudo")
                    }
                    IconButton(onClick = { viewModel.send(TvCommand.VolumeDown) }) {
                        Icon(Icons.Default.VolumeDown, contentDescription = "Volume -")
                    }
                }
                // D-Pad central
                Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { viewModel.send(TvCommand.Up) },
                        modifier = Modifier.align(Alignment.TopCenter)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Cima")
                    }
                    IconButton(onClick = { viewModel.send(TvCommand.Down) },
                        modifier = Modifier.align(Alignment.BottomCenter)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Baixo")
                    }
                    IconButton(onClick = { viewModel.send(TvCommand.Left) },
                        modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Esquerda")
                    }
                    IconButton(onClick = { viewModel.send(TvCommand.Right) },
                        modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Direita")
                    }
                    FilledIconButton(
                        onClick = { viewModel.send(TvCommand.Ok) },
                        modifier = Modifier.size(56.dp)
                    ) { Text("OK") }
                }
                // Canal
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CH", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { viewModel.send(TvCommand.ChannelUp) }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Canal +")
                    }
                    IconButton(onClick = { viewModel.send(TvCommand.ChannelDown) }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Canal -")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Home e Back
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.send(TvCommand.Back) }) {
                    // Android 15+: usar AutoMirrored para ícones direcionais
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar TV")
                }
                IconButton(onClick = { viewModel.send(TvCommand.Home) }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
                IconButton(onClick = { viewModel.send(TvCommand.Menu) }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Controles de mídia
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.send(TvCommand.Rewind) }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Retroceder")
                }
                IconButton(onClick = { viewModel.send(TvCommand.Play) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = { viewModel.send(TvCommand.Pause) }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
                IconButton(onClick = { viewModel.send(TvCommand.Stop) }) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
                IconButton(onClick = { viewModel.send(TvCommand.FastForward) }) {
                    Icon(Icons.Default.FastForward, contentDescription = "Avançar")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Teclado virtual
            OutlinedButton(onClick = { showKeyboard = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Teclado Virtual")
            }
        }
    }

    if (showKeyboard) {
        AlertDialog(
            onDismissRequest = { showKeyboard = false },
            title = { Text("Enviar texto para TV") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Digite aqui") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.send(TvCommand.SendText(textInput))
                        textInput = ""
                        showKeyboard = false
                    }
                }) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { showKeyboard = false }) { Text("Cancelar") }
            }
        )
    }
}
```

---

## 13. ViewModel e Camada de Apresentação

Crie `presentation/viewmodel/RemoteControlViewModel.kt`:

```kotlin
package com.antoniosilva.universaltvremote.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoniosilva.universaltvremote.data.remote.TvConnectorFactory
import com.antoniosilva.universaltvremote.domain.model.ConnectionState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice
import com.antoniosilva.universaltvremote.domain.repository.TvConnector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoteUiState(
    val deviceName: String = "",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RemoteControlViewModel @Inject constructor(
    private val connectorFactory: TvConnectorFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private var connector: TvConnector? = null

    fun connectTo(device: TvDevice) {
        connector = connectorFactory.getConnector(device.brand)
        _uiState.update { it.copy(deviceName = device.name, isLoading = true) }

        viewModelScope.launch {
            connector?.connectionState()?.collect { state ->
                _uiState.update { it.copy(connectionState = state, isLoading = false) }
            }
        }

        viewModelScope.launch {
            connector?.connect(device)
                ?.onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun send(command: TvCommand) {
        viewModelScope.launch {
            connector?.sendCommand(command)
                ?.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun disconnect() {
        viewModelScope.launch { connector?.disconnect() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
```

---

## 14. Injeção de Dependência com Hilt

Crie `di/AppModule.kt`. 
> **🛡️ Segurança de Rede:** Na Etapa 1 configuramos o `network_security_config.xml` para aceitar HTTP na rede local. Para blindar o app contra vulnerabilidades, o código abaixo introduz o `LocalNetworkSecurityInterceptor`. Esse interceptor garante que requisições HTTP sem criptografia sejam abortadas caso o destino seja externo (Internet pública), protegendo contra ataques *Man-in-the-Middle* (MitM).

```kotlin
package com.antoniosilva.universaltvremote.di

import android.content.Context
import androidx.room.Room
import com.antoniosilva.universaltvremote.data.local.AppDatabase
import com.antoniosilva.universaltvremote.data.local.dao.PairedTvDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class LocalNetworkSecurityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isHttp = request.url.scheme == "http"
        val host = request.url.host

        // Considera rede privada (10.x.x.x, 172.16-31.x.x, 192.168.x.x) ou Localhost
        val isPrivateIp = host.startsWith("192.168.") || 
                          host.startsWith("10.") || 
                          host.startsWith("127.0.")
        
        if (isHttp && !isPrivateIp) {
            throw SecurityException("Bloqueado MitM: Tráfego HTTP não criptografado só é permitido para IPs locais privados. Host suspeito: $host")
        }
        
        return chain.proceed(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(LocalNetworkSecurityInterceptor()) // Trava de Segurança
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "universaltvremote.db")
            .fallbackToDestructiveMigration() // trocar por migrações reais em produção
            .build()

    @Provides
    @Singleton
    fun providePairedTvDao(database: AppDatabase): PairedTvDao =
        database.pairedTvDao()
}
```

### 🔁 **O Ciclo do Git Flow (Fim da Etapa e Merge)**

Chegamos ao final da **Etapa 2**. Toda a arquitetura MVVM, DI com Hilt e UI base foram criadas. Chegou a hora de registrar isso e juntar (*merge*) nossa branch `feature/core-network` de volta à branch principal de desenvolvimento (`develop`).

```bash
# 1. Faça o commit final da camada de apresentação (ViewModels e Hilt DI)
git add app/src/main/java/com/antoniosilva/universaltvremote/presentation/
git add app/src/main/java/com/antoniosilva/universaltvremote/di/
git commit -m "feat(presentation): adicionar ViewModels, telas e injeção do Hilt"

# 2. Mude de volta para a branch de integração contínua (develop)
git checkout develop

# 3. Traga (merge) as novidades finalizadas da feature para a develop
git merge feature/core-network

# 4. Envie a develop atualizada para o repositório remoto (GitHub)
git push origin develop
```


