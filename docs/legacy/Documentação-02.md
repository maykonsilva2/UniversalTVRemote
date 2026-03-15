# Passo a Passo Completo: App "Universal TV Remote" para Android

Este guia detalha o desenvolvimento de um aplicativo Android que transforma seu dispositivo em um controle remoto universal para Smart TVs via Wi-Fi. Utilizaremos **Kotlin**, **Android Studio**, **Git** para controle de versão e **Docker** para garantir um ambiente de desenvolvimento consistente e facilitar testes com um simulador de TV.

## Índice

1. [Configuração Inicial do Projeto](#1-configuração-inicial-do-projeto)
2. [Arquitetura do App](#2-arquitetura-do-app)
3. [Gerenciamento de Dependências e Permissões](#3-gerenciamento-de-dependências-e-permissões)
4. [Descoberta de Dispositivos na Rede](#4-descoberta-de-dispositivos-na-rede)
5. [Emparelhamento e Conexão com Diferentes Marcas](#5-emparelhamento-e-conexão-com-diferentes-marcas)
6. [Envio de Comandos (Controles Básicos e de Mídia)](#6-envio-de-comandos-controles-básicos-e-de-mídia)
7. [Interface do Usuário com Jetpack Compose](#7-interface-do-usuário-com-jetpack-compose)
8. [Persistência de Dados (TVs Pareadas)](#8-persistência-de-dados-tvs-pareadas)
9. [Testes Unitários, de Integração e UI](#9-testes-unitários-de-integração-e-ui)
10. [Controle de Versão com Git](#10-controle-de-versão-com-git)
11. [Uso do Docker para Ambiente de Desenvolvimento e Testes](#11-uso-do-docker-para-ambiente-de-desenvolvimento-e-testes)
12. [Build e Assinatura para Publicação](#12-build-e-assinatura-para-publicação)
13. [Publicação na Google Play Store](#13-publicação-na-google-play-store)
14. [Pós-lançamento e Manutenção](#14-pós-lançamento-e-manutenção)

---

## 1. Configuração Inicial do Projeto

### 1.1 Instalar Android Studio e SDKs
- Baixe e instale o [Android Studio](https://developer.android.com/studio) (versão mais recente).
- Durante a instalação, inclua o SDK do Android 14 (API 34) e ferramentas de build.
- Certifique-se de que o **Kotlin** está habilitado.

### 1.2 Criar Novo Projeto
- Abra o Android Studio e clique em **New Project**.
- Escolha a template **Empty Activity**.
- Configure:
  - **Name**: UniversalTVRemote
  - **Package name**: com.antoniosilva.universaltvremote
  - **Language**: Kotlin
  - **Minimum SDK**: API 24 (Android 7.0) – para cobrir a maioria dos dispositivos, mantendo recursos modernos.
  - **Build configuration language**: Kotlin DSL (build.gradle.kts)

### 1.3 Inicializar Repositório Git
```bash
cd UniversalTVRemote
git init
echo "*.iml" >> .gitignore
echo ".idea/" >> .gitignore
echo "build/" >> .gitignore
echo "local.properties" >> .gitignore
git add .
git commit -m "Initial commit: empty Android project"
```

### 1.4 Configurar Docker (opcional, para ambiente de build e testes)
Crie um `Dockerfile` na raiz do projeto para um ambiente de build Android:
```dockerfile
FROM openjdk:11-jdk

ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH $PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin

RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools \
    && cd $ANDROID_SDK_ROOT/cmdline-tools \
    && wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip \
    && unzip commandlinetools-linux-*.zip -d latest \
    && rm *.zip

RUN yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses
RUN $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

WORKDIR /app
COPY . .
RUN ./gradlew build
```
Esse container pode ser usado para compilar o app de forma reproduzível. Para facilitar, crie um `docker-compose.yml` para executar um servidor que simule uma TV (mais detalhes na seção 11).

## 2. Arquitetura do App

Adotaremos a arquitetura **MVVM** (Model-View-ViewModel) com **Clean Architecture** em camadas:

- **Data Layer**: Repositórios, fontes de dados (Room, Retrofit, WebSocket, Discovery).
- **Domain Layer**: Casos de uso (use cases) que orquestram a lógica de negócio.
- **Presentation Layer**: ViewModels e Composables (Jetpack Compose).

Utilizaremos **Dagger Hilt** para injeção de dependência.

### Configurar Dagger Hilt
No arquivo `build.gradle.kts` do módulo app:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    // ...
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    // ...
}
```
No arquivo `build.gradle.kts` do projeto (raiz):
```kotlin
plugins {
    id("com.google.dagger.hilt.android") version "2.48" apply false
}
```

## 3. Gerenciamento de Dependências e Permissões

### 3.1 Permissões no AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- Se quiser usar localização para Wi-Fi (Android 10+), necessário para escaneamento -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="28" />
<!-- Para Android 12+ (API 31+), permissão para descoberta de dispositivos próximos -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

### 3.2 Dependências principais (build.gradle.kts do app)
```kotlin
dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room
    implementation("androidx.room:room-runtime:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Para WebSocket (usaremos OkHttp que já tem suporte)
    // Para SSDP/UPnP - biblioteca jupnp
    implementation("org.jupnp:org.jupnp:2.7.0")
    implementation("org.jupnp:org.jupnp.android:2.7.0")

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Testes
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

## 4. Descoberta de Dispositivos na Rede

A descoberta será feita usando protocolos padrão: **SSDP (UPnP)** e **mDNS (NSD)**. Vamos implementar um serviço que escaneia a rede e notifica a UI.

### 4.1 Criar modelo de dados para dispositivo TV
```kotlin
data class TvDevice(
    val id: String,           // endereço MAC ou identificador único
    val name: String,          // nome amigável (ex: "Samsung QLED 55")
    val manufacturer: String,  // "Samsung", "LG", "Roku", etc.
    val ipAddress: String,
    val port: Int,             // porta do serviço (ex: 8001 para Samsung, 8080 para LG)
    val serviceType: String,   // "urn:samsung.com:service:MainTVService2:1", etc.
    val location: String       // URL base para comandos
)
```

### 4.2 Implementar descoberta com jupnp (SSDP)
Crie um serviço que utilize `jupnp` para buscar dispositivos UPnP. Exemplo básico:

```kotlin
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.model.meta.Device
import org.jupnp.registry.RegistryListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDiscovery @Inject constructor() {
    private var upnpService: UpnpService? = null

    fun startDiscovery(onDeviceFound: (TvDevice) -> Unit) {
        upnpService = UpnpServiceImpl()
        val listener = object : RegistryListener {
            override fun remoteDeviceAdded(registry: org.jupnp.registry.Registry?, device: Device<*, *, *>?) {
                device?.let { parseDevice(it) }?.let { tvDevice ->
                    onDeviceFound(tvDevice)
                }
            }
            // outros métodos omitidos...
        }
        upnpService?.registry?.addListener(listener)
        upnpService?.controlPoint?.search()
    }

    private fun parseDevice(device: Device<*, *, *>): TvDevice? {
        // Extrair informações: fabricante, nome, URL base, etc.
        val manufacturer = device.details?.manufacturerDetails?.manufacturer?.toLowerCase()
        val name = device.details?.friendlyName
        val ip = device.identity?.descriptorURL?.host
        val port = device.identity?.descriptorURL?.port
        // Identificar tipo de serviço para comandos
        // ...
        return TvDevice(
            id = device.identity?.udn?.identifierString ?: "",
            name = name ?: "Desconhecido",
            manufacturer = manufacturer ?: "",
            ipAddress = ip ?: "",
            port = port ?: 0,
            serviceType = device.type?.type ?: "",
            location = device.identity?.descriptorURL?.toString() ?: ""
        )
    }
}
```

### 4.3 Para mDNS (Network Service Discovery)
Podemos usar a API nativa `NsdManager`:
```kotlin
class MdnsDiscoverer(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) { }
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            // Resolver para obter endereço IP e porta
            nsdManager.resolveService(serviceInfo, resolveListener)
        }
        // ...
    }
    fun start() {
        nsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
}
```

### 4.4 Integrar com ViewModel
Use um `ViewModel` que inicia a descoberta e expõe uma `StateFlow` com a lista de dispositivos.

## 5. Emparelhamento e Conexão com Diferentes Marcas

Cada fabricante tem seu próprio protocolo. Vamos criar uma arquitetura extensível:

### 5.1 Interface `TvConnector`
```kotlin
interface TvConnector {
    suspend fun pair(device: TvDevice, onPinRequest: (String) -> Unit): Boolean
    suspend fun connect(device: TvDevice): Boolean
    fun disconnect()
    fun sendCommand(command: TvCommand)
}
```

### 5.2 Implementações por marca
- **Samsung (Tizen)**: Utilizam WebSocket na porta 8001 ou 8002. O emparelhamento geralmente exibe um PIN na TV e o app deve enviá-lo de volta. Podemos usar a biblioteca [samsung-tv-ws-api](https://github.com/kdssoftware/samsung-tv-ws-api) ou implementar manualmente.
- **LG (webOS)**: Protocolo via WebSocket na porta 3000 ou 3001. Usa handshake com chave de cliente. O app deve enviar um par de chaves e a TV exibe um PIN. [Documentação](https://webostv.developer.lge.com/develop/webos-network-remote-control).
- **Roku**: API HTTP (ECP). Não precisa de emparelhamento, basta enviar comandos para o IP: `http://<ip>:8060/keypress/Home`.
- **Android TV / Google TV**: Usam o protocolo [Google TV Remote](https://developers.google.com/cast/docs/android_sender) (baseado em Cast). É necessário pareamento via QR Code ou PIN.

### 5.3 Factory para obter o conector correto
```kotlin
@Singleton
class TvConnectorFactory @Inject constructor(
    private val samsungConnector: SamsungConnector,
    private val lgConnector: LgConnector,
    private val rokuConnector: RokuConnector,
    private val androidTvConnector: AndroidTvConnector
) {
    fun getConnector(manufacturer: String): TvConnector? {
        return when (manufacturer.lowercase()) {
            "samsung" -> samsungConnector
            "lg" -> lgConnector
            "roku" -> rokuConnector
            "google", "android tv", "sony", "philips" -> androidTvConnector
            else -> null
        }
    }
}
```

## 6. Envio de Comandos (Controles Básicos e de Mídia)

### 6.1 Modelo de comando
```kotlin
sealed class TvCommand {
    object PowerOn : TvCommand()
    object PowerOff : TvCommand()
    class VolumeUp(val steps: Int = 1) : TvCommand()
    class VolumeDown(val steps: Int = 1) : TvCommand()
    object Mute : TvCommand()
    class ChannelUp(val steps: Int = 1) : TvCommand()
    class ChannelDown(val steps: Int = 1) : TvCommand()
    class Navigate(direction: Direction) : TvCommand()
    class KeyPress(val key: String) : TvCommand() // para teclas numéricas e especiais
    class TextInput(val text: String) : TvCommand()
    // ...
}

enum class Direction { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME }
```

### 6.2 Implementação no conector
Exemplo para Roku (HTTP simples):
```kotlin
class RokuConnector @Inject constructor(
    private val okHttpClient: OkHttpClient
) : TvConnector {
    override suspend fun pair(device: TvDevice, onPinRequest: (String) -> Unit): Boolean {
        // Roku não precisa de pareamento
        return true
    }
    override suspend fun connect(device: TvDevice): Boolean {
        // apenas guardar IP, sem conexão persistente
        return true
    }
    override fun disconnect() { }
    override fun sendCommand(command: TvCommand) {
        val url = "http://${device.ipAddress}:8060/keypress/${command.toRokuKey()}"
        val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()
        okHttpClient.newCall(request).enqueue(...)
    }
}
```

## 7. Interface do Usuário com Jetpack Compose

### 7.1 Estrutura de navegação
Usaremos `Navigation Compose`. Telas:
- `DeviceListScreen`: lista de TVs descobertas e pareadas.
- `RemoteControlScreen`: controles da TV selecionada.
- `PairingScreen`: exibe PIN e aguarda confirmação.

### 7.2 Layout responsivo
Para se adaptar ao Galaxy S21 FE (telefone) e POCO Pad (tablet), usaremos `WindowSizeClass` e `Modifier` para ajustar a disposição dos botões.

Exemplo de detecção de tamanho:
```kotlin
val configuration = LocalConfiguration.current
val screenWidth = configuration.screenWidthDp
val isTablet = screenWidth >= 600
```

No tablet, podemos exibir o controle direcional à esquerda e uma área de trackpad ou lista de apps à direita.

### 7.3 Componentes de controle
- **Botões circulares** para navegação.
- **Slider** para volume (se a TV suportar).
- **Teclado numérico** modal.
- **Trackpad** para simular movimento do cursor (em TVs que suportam).

### 7.4 Teclado virtual
Ao tocar em um campo de busca na TV, o app exibe um teclado customizado. Podemos usar o teclado padrão do Android e enviar cada tecla digitada para a TV. Para isso, precisamos de um `TextField` com foco e um listener.

## 8. Persistência de Dados (TVs Pareadas)

Usaremos **Room** para armazenar as TVs que o usuário já configurou.

### 8.1 Entidade `PairedTv`
```kotlin
@Entity(tableName = "paired_tvs")
data class PairedTv(
    @PrimaryKey val id: String,
    val name: String,
    val manufacturer: String,
    val ipAddress: String,
    val port: Int,
    val authToken: String?,
    val lastUsed: Long
)
```

### 8.2 DAO
```kotlin
@Dao
interface PairedTvDao {
    @Query("SELECT * FROM paired_tvs ORDER BY lastUsed DESC")
    fun getAll(): Flow<List<PairedTv>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tv: PairedTv)

    @Delete
    suspend fun delete(tv: PairedTv)
}
```

### 8.3 Repositório
```kotlin
@Singleton
class TvRepository @Inject constructor(
    private val dao: PairedTvDao
) {
    fun getPairedTvs(): Flow<List<PairedTv>> = dao.getAll()
    suspend fun saveTv(tv: PairedTv) = dao.insert(tv)
    suspend fun removeTv(tv: PairedTv) = dao.delete(tv)
}
```

## 9. Testes Unitários, de Integração e UI

### 9.1 Testes unitários
- ViewModels com `MockK` e `kotlinx.coroutines.test`.
- Repositórios com banco em memória (Room in-memory).

Exemplo de teste para DiscoveryViewModel:
```kotlin
@Test
fun `when discovery starts, devices are added to list`() = runTest {
    val mockDiscovery = mockk<DeviceDiscovery>()
    val viewModel = DiscoveryViewModel(mockDiscovery)
    // Simular descoberta
    viewModel.startDiscovery()
    // ...
}
```

### 9.2 Testes de integração
- Usar `MockWebServer` (OkHttp) para simular respostas HTTP das TVs.
- Para WebSocket, podemos usar um servidor local com `MockWebServer` ou implementar um `WebSocketListener` de teste.

### 9.3 Testes de UI (Compose)
- Utilizar `ComposeTestRule` para testar interações na interface.

### 9.4 Testes em dispositivos reais
- Galaxy S21 FE e POCO Pad: testar usabilidade, rotação, multitarefa.
- Testar com diferentes marcas de TV (se disponíveis) ou usar simuladores.

## 10. Controle de Versão com Git

### 10.1 Estratégia de branches
- `main`: branch de produção.
- `develop`: branch de integração.
- `feature/*`: para novas funcionalidades.
- `hotfix/*`: para correções urgentes.

### 10.2 Commits semânticos
Seguir conventional commits: `feat:`, `fix:`, `docs:`, `test:`, etc.

### 10.3 .gitignore
Já configurado no início, mas incluir também:
```
/captures/
.externalNativeBuild/
*.keystore
google-services.json
```

### 10.4 Hooks (opcional)
Usar `pre-commit` para rodar `detekt` ou `ktlint`.

## 11. Uso do Docker para Ambiente de Desenvolvimento e Testes

### 11.1 Container para build reproduzível
O Dockerfile já criado na seção 1.4 permite compilar o app dentro de um container Linux com Android SDK. Útil para CI/CD.

### 11.2 Simulador de TV em container
Para testar a descoberta e comandos sem uma TV física, podemos criar um servidor que implementa os protocolos básicos (SSDP, WebSocket, HTTP). Exemplo em Node.js ou Python dentro de um container Docker.

Crie um diretório `tv-simulator` com um Dockerfile:
```dockerfile
FROM python:3.9-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY simulator.py .
CMD ["python", "simulator.py"]
```
O `simulator.py` pode anunciar serviços SSDP e responder a comandos HTTP/WebSocket.

### 11.3 Docker Compose para orquestrar
```yaml
version: '3.8'
services:
  tv-simulator:
    build: ./tv-simulator
    network_mode: host   # para que o app Android (no emulador ou dispositivo na mesma rede) enxergue
  android-builder:
    build: .
    volumes:
      - .:/app
    command: ./gradlew build
```
Assim, podemos executar `docker-compose up` para iniciar o simulador e depois compilar o app.

## 12. Build e Assinatura para Publicação

### 12.1 Gerar keystore
```bash
keytool -genkey -v -keystore universalremote.keystore -alias universalremote -keyalg RSA -keysize 2048 -validity 10000
```

### 12.2 Configurar assinatura no build.gradle.kts
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("universalremote.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "universalremote"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

### 12.3 Gerar App Bundle
```bash
./gradlew bundleRelease
```
O arquivo `.aab` estará em `app/build/outputs/bundle/release/`.

## 13. Publicação na Google Play Store

### 13.1 Criar conta de desenvolvedor
Acesse [Google Play Console](https://play.google.com/console) e pague a taxa única.

### 13.2 Preparar materiais
- Ícones (512x512, 256x256).
- Screenshots (pelo menos 2 de telefone e 2 de tablet).
- Descrição curta (80 caracteres) e completa (até 4000).
- Categoria: "Ferramentas" ou "Estilo de vida".
- Política de privacidade (hospedar em algum lugar, ex: GitHub Pages).

### 13.3 Fazer upload do App Bundle
- Preencher o formulário de classificação de conteúdo.
- Definir preço (grátis) e países.
- Revisar e enviar para produção.

## 14. Pós-lançamento e Manutenção

### 14.1 Monitoramento
- Integrar **Firebase Crashlytics** para relatar erros.
- Usar **Google Analytics** para entender uso.

### 14.2 Atualizações
- Corrigir bugs reportados.
- Adicionar suporte a novas marcas de TV conforme demanda.
- Acompanhar mudanças nos protocolos das fabricantes.

### 14.3 Feedback dos usuários
- Responder comentários na Play Store.
- Criar um e-mail de suporte ou grupo no Telegram.

---

Seguindo esse passo a passo, você terá um aplicativo funcional, bem estruturado e pronto para ser publicado. Lembre-se de testar exaustivamente em diferentes TVs e redes. Boa sorte!