package com.antoniosilva.universaltvremote.domain.model

sealed class ConnectionsState {
    object Disconnected : ConnectionsState()
    object Connecting : ConnectionsState()
    data class Connected(val device: TvDevice) : ConnectionsState()
    data class Error(val message: String) : ConnectionsState()
}