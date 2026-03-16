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
