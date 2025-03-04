package net.hellz.util

import kotlinx.coroutines.runBlocking
import net.hellz.utils.LettuceConnection
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.kyori.adventure.text.Component

class InstanceManager {

    private val lobbies = mutableListOf<InstanceContainer>()

    init {
        val globalEventHandler = MinecraftServer.getGlobalEventHandler()

        globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player

            // Assign player to a random lobby
            val assignedLobby = lobbies.random()
            event.spawningInstance = assignedLobby

            // Set player spawn point
            player.respawnPoint = Pos(0.0, 42.0, 0.0)
            player.setGameMode(GameMode.ADVENTURE)

            // Give player an item
            player.inventory.setItemStack(
                4, ItemStack.of(Material.ENCHANTED_BOOK)
                    .withCustomName(Component.text("§bServer Selector"))
            )

            player.inventory.setItemStack(
                8, ItemStack.of(Material.NETHER_STAR)
                    .withCustomName(Component.text("§bLobby Selector"))
            )
            runBlocking { updatePlayerCountInRedis("lobby${lobbies.indexOf(assignedLobby) + 1}", 1) }
        }

        globalEventHandler.addListener(PlayerDisconnectEvent::class.java) { event ->
            val player = event.player
            val assignedLobby = player.instance as? InstanceContainer
            val instanceId = "lobby${lobbies.indexOf(assignedLobby) + 1}"

            runBlocking { updatePlayerCountInRedis(instanceId, -1) }
        }
    }

    fun addLobby(lobby: InstanceContainer) {
        lobbies.add(lobby)
    }

    suspend fun registerLobbyToRedis(instanceId: String) {
        val key = "servers:lobbies:$instanceId"
        LettuceConnection.asyncCommands.hset(key, mapOf(
            "status" to "online", // Lobby status
            "playerCount" to "0"   // Initial player count
        ))
    }

    suspend fun updatePlayerCountInRedis(instanceId: String, increment: Int) {
        val key = "servers:lobbies:$instanceId"
        LettuceConnection.asyncCommands.hincrby(key, "playerCount", increment.toLong())
    }
}