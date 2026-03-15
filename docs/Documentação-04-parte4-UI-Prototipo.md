# 📱 Universal TV Remote App — Implementação da UI (Protótipo)
> **Parte 4**: Traduzindo o Protótipo HTML/CSS (Tailwind) para Jetpack Compose
> Esta documentação detalha a conversão do design moderno (graus de *glassmorphism*, botões táteis e gradientes) em código Kotlin puro usando o Jetpack Compose.

---

# 🎨 ETAPA 3: Interface Glassmorphism & UI
Nesta etapa, traduzimos o protótipo HTML/CSS original em telas nativas do Android usando Jetpack Compose. Vamos criar os componentes táteis translúcidos e conectá-los com a lógica de rede da Etapa 2.

## 1. Configuração do Tema e Cores
1. [Configuração do Tema e Cores](#1-configuração-do-tema-e-cores)
2. [Componentes Base e Modificadores (Glassmorphism)](#2-componentes-base-e-modificadores-glassmorphism)
3. [Tela 1: Descoberta de Dispositivos (Device Discovery)](#3-tela-1-descoberta-de-dispositivos-device-discovery)
4. [Tela 2: Controle Remoto Principal (Main Remote)](#4-tela-2-controle-remoto-principal-main-remote)
5. [Tela 3: Atalhos de Apps (App Shortcuts)](#5-tela-3-atalhos-de-apps-app-shortcuts)
6. [Navegação Inferior (Bottom Nav Bar)](#6-navegação-inferior-bottom-nav-bar)

---

## 1. Configuração do Tema e Cores

O primeiro passo é mapear as cores do protótipo (Tailwind config) para o `Color.kt` do seu projeto Android.

### `app/src/main/java/com/.../presentation/theme/Color.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.theme

import androidx.compose.ui.graphics.Color

// Cores base do Protótipo
val PrimaryRed = Color(0xFFEF371F)
val BackgroundLight = Color(0xFFF8F6F6)
val BackgroundDark = Color(0xFF18120A) // Ou 0xFF221210 dependendo da tela
val SurfaceDark = Color(0xFF423B33)
val TextMuted = Color(0xFFBEB8B0)

// Cores Específicas do Controle
val NavBlue = Color(0xFF211181)
val NavHighlight = Color(0xFF4222D7)
val RemoteShellDark = Color(0x66000000) // Equivalente a bg-black/40
val GlassBorder = Color(0x4D334155)     // Equivalente a border-slate-700/30
```

### `app/src/main/java/com/.../presentation/theme/Theme.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun UniversalTVRemoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        // Adicione personalização de Typography (Spline Sans) se desejar
        content = content
    )
}
```

---

## 2. Componentes Base e Modificadores (Glassmorphism)

O protótipo usa sombras táteis (`tactile-shadow`) e fundos translúcidos (`backdrop-blur`). Vamos criar modificadores customizados para isso no Compose.

### `app/src/main/java/com/.../presentation/components/Modifiers.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Efeito de "casca" do controle remoto com borda de vidro
fun Modifier.glassmorphismShell(): Modifier = this
    .clip(RoundedCornerShape(48.dp))
    .background(RemoteShellDark) // Para blur real, requer RenderEffect (API 31+) ou lib externa (ex: Haze)
    .border(1.dp, GlassBorder, RoundedCornerShape(48.dp))

// Efeito tátil usado nos botões do controle
fun Modifier.tactileShadow(shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .shadow(
        elevation = 6.dp,
        shape = shape,
        spotColor = Color.Black.copy(alpha = 0.4f),
        ambientColor = Color.White.copy(alpha = 0.1f)
    )

// ⚡ PERFORMANCE / CASO EXTREMO: Debouncing em cliques UI
// Usado nos botões do controle remoto para evitar sobrecarregar
// o WebSocket/Http caso o usuário clique 50x por segundo na tela.
fun Modifier.debouncedClickable(
    debounceWindow: Long = 200L, // ms
    onClick: () -> Unit
): Modifier = androidx.compose.ui.composed {
    val lastClickTime = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0L) }
    this.clickable {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime.value >= debounceWindow) {
            lastClickTime.value = currentTime
            onClick()
        }
    }
}
```

---

## 3. Tela 1: Descoberta de Dispositivos (Device Discovery)

Esta tela realiza o "scanning" de TVs na mesma rede. Usa ícones arredondados e barras de progresso.

### `.../presentation/screens/discovery/DeviceDiscoveryScreen.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniosilva.universaltvremote.presentation.theme.*

@Composable
fun DeviceDiscoveryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 24.dp, 16.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Back */ }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Connect to TV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { /* Help */ }) {
                Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = Color.White)
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            // Scanning Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark.copy(alpha = 0.3f))
                    .border(1.dp, SurfaceDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Scanning for devices...", color = Color.White, fontWeight = FontWeight.SemiBold)
                    // PONTOS ANIMADOS AQUI (3 pontinhos da UI original)
                }
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { 0.65f },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = PrimaryRed,
                    trackColor = SurfaceDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Discovered Devices Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DISCOVERED DEVICES", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("3 found", color = PrimaryRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lista de Dispositivos Achados
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(3) { index ->
                    DeviceItemCard("Living Room TV", "Samsung 7 Series • 192.168.1.45")
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Manual IP Button
                    OutlinedButton(
                        onClick = { /* Add IP */ },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter IP address manually")
                    }
                }
            }
        }

        // Action Area / Scan Again Button
        Box(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { /* Scan Again */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Text("SCAN AGAIN", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeviceItemCard(name: String, details: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable { /* Conectar */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BackgroundDark.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(details, color = TextMuted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}
```

---

## 4. Tela 2: Controle Remoto Principal (Main Remote)

Esta é a tela principal com D-Pad colorido, fundo *glassmorphism*, botões em pílulas (Volume, Canal) e layout super compacto inspirado por controles físicos top de linha.

### `.../presentation/screens/remote/RemoteControlScreen.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.screens.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniosilva.universaltvremote.presentation.components.glassmorphismShell
import com.antoniosilva.universaltvremote.presentation.components.tactileShadow
import com.antoniosilva.universaltvremote.presentation.theme.*

@Composable
fun RemoteControlScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark), // Fundo principal da view
        contentAlignment = Alignment.Center
    ) {
        // "Carcaça" translúcida do Controle Remoto
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight(0.85f) // Aspect ratio aproximado (9/19 original)
                .glassmorphismShell()
                .padding(24.dp)
        ) {
            // Barra de Status Top [Cast Icon | CONNECTED | Settings]
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Green))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONNECTED", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
            }

            // Power Button (Botão Centralizado Vermelho)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { /* Aqui conectamos com a Etapa 2: viewModel.send(TvCommand.PowerOn) */ },
                    modifier = Modifier.size(80.dp).tactileShadow(CircleShape)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(PrimaryRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // D-Pad com Gradiente Radial Azul
            DPadComponent()

            Spacer(modifier = Modifier.height(32.dp))

            // Controle de Vol, Canal, Mute e Home
            ControlGridSection()
            
            Spacer(modifier = Modifier.weight(1f))

            // 3 App Shortcuts na base (Netflix, Youtube, Prime)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppShortcutMinimal("Netflix")
                AppShortcutMinimal("Youtube")
                AppShortcutMinimal("Prime")
            }
        }
    }
}

@Composable
fun DPadComponent() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Base Circular do DPad
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(NavHighlight, NavBlue)))
                .tactileShadow(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Setas Direcionais
            // Observe que cada clique aqui seria enviado ao RemoteViewModel
            // ⚡ PERFORMANCE: Use o debouncedClickable (criado nos Modifiers) para botões D-Pad para não sobrecarregar
            // as chamadas de rede se o usuário pressionar furiosamente.
            IconButton(onClick = { /* debounce calls here */ }, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { /* viewModel.send(TvCommand.Down) */ }, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { /* viewModel.send(TvCommand.Left) */ }, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { /* viewModel.send(TvCommand.Right) */ }, modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)) {
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }

            // Botão OK Central
            Button(
                onClick = { /* viewModel.send(TvCommand.Ok) */ },
                modifier = Modifier.size(96.dp).tactileShadow(CircleShape),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = NavHighlight)
            ) {
                Text("OK", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun ControlGridSection() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Volume Rocker
        RockerButton(upIcon = Icons.Default.Add, downIcon = Icons.Default.Remove, label = "VOL")
        
        // Coluna central (Mute + Home)
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            CircleButton(Icons.Default.VolumeOff)
            CircleButton(Icons.Default.Home)
        }

        // Channel Rocker
        RockerButton(upIcon = Icons.Default.KeyboardArrowUp, downIcon = Icons.Default.KeyboardArrowDown, label = "CH")
    }
}

@Composable
fun RockerButton(upIcon: androidx.compose.ui.graphics.vector.ImageVector, downIcon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
            .tactileShadow(RoundedCornerShape(30.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = {}, modifier = Modifier.weight(1f).fillMaxWidth()) { Icon(upIcon, null, tint = Color.LightGray) }
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = {}, modifier = Modifier.weight(1f).fillMaxWidth()) { Icon(downIcon, null, tint = Color.LightGray) }
    }
}

@Composable
fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    IconButton(
        onClick = {},
        modifier = Modifier
            .size(52.dp)
            .background(Color(0xFF1E293B).copy(alpha = 0.8f), CircleShape)
            .tactileShadow(CircleShape)
    ) {
        Icon(icon, null, tint = Color.LightGray)
    }
}

@Composable
fun RowScope.AppShortcutMinimal(name: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.4f))
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Text(name.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha=0.6f))
    }
}
```

---

## 5. Tela 3: Atalhos de Apps (App Shortcuts)

Esta tela é responsável por exibir uma grande barra de busca e uma grade de aplicativos instalados na TV, para inicialização rápida.

### `.../presentation/screens/apps/AppShortcutsScreen.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage // Necessita coil-compose
import com.antoniosilva.universaltvremote.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShortcutsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // App Bar
        TopAppBar(
            title = { Text("Living Room TV", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White) },
            navigationIcon = { /* Ícone da TV */ },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PrimaryRed)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
        )

        // Barra de Busca
        Box(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryRed.copy(alpha = 0.1f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryRed.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search apps or content", color = PrimaryRed.copy(alpha = 0.4f), fontSize = 16.sp)
            }
        }

        // Título de Categoria
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Launch", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("8 RUNNING", color = PrimaryRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Grid de Aplicativos (2 Colunas)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            val apps = listOf(
                "Netflix" to "https://link-to-netflix-icon.png",
                "YouTube" to "https://link-to-youtube-icon.png",
                "Prime Video" to "https://link-to-prime-icon.png",
                "Disney+" to "https://link-to-disney-icon.png"
                // No projeto real, utilize drawable ou URIs do backend
            )
            
            items(apps.size) { i ->
                AppGridItem(name = apps[i].first, iconUrl = apps[i].second)
            }
        }
    }
}

@Composable
fun AppGridItem(name: String, iconUrl: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryRed.copy(alpha = 0.1f))
            .clickable { /* Abrir app */ }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = iconUrl,
            contentDescription = "$name logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
            // .set placeholder se nao houver internet 
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
```

---

## 6. Navegação Inferior (Bottom Nav Bar)

A navegação fixa na parte de baixo (`BottomNavigation` do Jetpack Compose). Este componente englobará suas telas principais.

### `.../presentation/components/BottomNavBar.kt`
```kotlin
package com.antoniosilva.universaltvremote.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antoniosilva.universaltvremote.presentation.theme.BackgroundDark
import com.antoniosilva.universaltvremote.presentation.theme.PrimaryRed

data class BottomNavItem(
    val name: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun UniversalBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem("Remote", Icons.Default.Cast, "remote_route"),
        BottomNavItem("Apps", Icons.Default.GridView, "apps_route"),
        BottomNavItem("Devices", Icons.Default.Devices, "discovery_route")
    )

    NavigationBar(
        modifier = Modifier
            .shadow(10.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        containerColor = BackgroundDark.copy(alpha = 0.95f),
        contentColor = Color.Gray
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.name) },
                label = { Text(item.name.uppercase()) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryRed,
                    selectedTextColor = PrimaryRed,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
```

---
**Fim da Parte 4.** Copie e cole os scripts equivalentes dentro de seus pacotes especificados para ver a UI perfeitamente espelhada em Jetpack Compose.
