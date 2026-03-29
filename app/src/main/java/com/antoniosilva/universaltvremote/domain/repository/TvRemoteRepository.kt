package com.antoniosilva.universaltvremote.domain.repository

import com.antoniosilva.universaltvremote.domain.model.ConnectionsState
import com.antoniosilva.universaltvremote.domain.model.TvCommand
import com.antoniosilva.universaltvremote.domain.model.TvDevice

import  kotlinx.coroutines.flow.Flow

interface TvRemoteRepository {
    fun connectionState() : Flow<ConnectionsState>
    suspend fun connect(device: TvDevice) : Result<Unit>
    suspend fun disconnect()
    suspend fun sendCommand(command: TvCommand) : Result<Unit>
    suspend fun getInstalledApps(): Result<List<TvApp>>
}

data class TvApp(
    val id: String,
    val name: String,
    val iconUrl: String? = null
)