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
            // Configuração de assinatura
            // TODO: Implementar assinatura de release:  Como a chave criptográfica (.jks) ainda não foi provisionada no sistema de arquivos local, a sua invocação abortará a sincronização do Gradle. Mantenha comentado por enquanto Na IDE
            //signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
}