package com.antoniosilva.universaltvremote.domain.model

data class TvDevice(
    val id: String,              // MAC address ou UUID único
    val name: String,            // Nome da TV Ex: "Samsung QLED 55"
    val brand: TvBrand,          // Marca da TV em enum class TvBrand
    val ipAddress: String,       // IPV4 da TV
    val port: Int,               // Porta de comunicação da TV EX: // 8001/8002 Samsung, 3000 LG, 8060 Roku
    val model: String? = null,
    val isPaired: Boolean = false,      // Se a TV está conectada ao UniversalTVRemote

    // 🛡️ SEGURANÇA: Em produção, tokens e chaves não devem ser salvos em texto plano (SharedPreferences ou Room).
    // Recomenda-se o uso de 'androidx.security:security-crypto' para persistência cifrada de dados sensíveis.
    // Para fins didáticos, este projeto foca na Clean Architecture e Hilt; a persistência será feita
    // via Room Database (Seção 11 da documentação) de forma simples, deixando a criptografia como uma melhoria futura.

    val authToken: String? = null       // Token de autenticação

)

enum class TvBrand {
    SAMSUNG,
    LG,
    ROKU,
    SONY,
    ANDROID_TV,
    UNKNOWN
}