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
    data class LaunchApp(val appId : String) : TvCommand()

}