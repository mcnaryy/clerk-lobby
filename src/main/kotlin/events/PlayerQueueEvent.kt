package net.hellz.events

import kotlinx.coroutines.*
import net.hellz.LobbyBoard
import net.hellz.util.VelocityChannel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player as MinestomPlayer
import java.util.*

class PlayerQueueEvent {

    private val queues: MutableMap<String, PriorityQueue<Player>> = mutableMapOf()
    private val playerJobs: MutableMap<UUID, Job> = mutableMapOf()
    private val pausedQueues: MutableSet<String> = mutableSetOf()
    private val velocityChannel = VelocityChannel()

    suspend fun queuePlayer(queueName: String, player: Player, minestomPlayer: MinestomPlayer) {
        val queue = queues.getOrPut(queueName) {
            PriorityQueue(compareByDescending { it.rankWeight })
        }
        val previousQueue = queue.toList()

        if (queue.contains(player)) {
            queue.remove(player)
            playerJobs[player.uuid]?.cancel()
            playerJobs.remove(player.uuid)
            minestomPlayer.sendMessage(
                Component.text("You have left your queue position for $queueName.", NamedTextColor.YELLOW)
            )
            LobbyBoard.updateQueue(minestomPlayer, null, queueName, queue.size)
        } else {
            queue.add(player)
            minestomPlayer.sendMessage(
                Component.text("You have entered the $queueName queue.", NamedTextColor.GREEN)
            )
            startPositionUpdates(queueName, player, minestomPlayer)
        }

        queue.filter { it.uuid != player.uuid }.forEachIndexed { index, queuedPlayer ->
            val queuedMinestomPlayer = getPlayerByUUID(queuedPlayer.uuid)
            if (queuedMinestomPlayer != null) {
                LobbyBoard.updateQueue(queuedMinestomPlayer, index + 1, queueName, queue.size)
            }
        }

        notifyPriorityChanges(queueName, previousQueue)
        processQueue(queueName)
    }

    private fun startPositionUpdates(queueName: String, player: Player, minestomPlayer: MinestomPlayer) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                sendPositionUpdate(queueName, player, minestomPlayer)
                delay(30_000)
            }
        }
        playerJobs[player.uuid] = job
    }

    private suspend fun sendPositionUpdate(queueName: String, player: Player, minestomPlayer: MinestomPlayer) {
        val queue = queues[queueName] ?: return
        val position = queue.toList().indexOfFirst { it.uuid == player.uuid } + 1
        minestomPlayer.sendMessage(
            Component.text(
                "\n §6§o⌖ §e§oYou are currently position §6§o§n#$position§r §e§oout of ${queue.size}.\n",
                NamedTextColor.YELLOW
            )
        )
        LobbyBoard.updateQueue(minestomPlayer, position, queueName, queue.size)
    }

    private fun notifyPriorityChanges(queueName: String, previousQueue: List<Player>) {
        val queue = queues[queueName] ?: return
        val currentQueue = queue.toList()

        val previousMap = previousQueue.withIndex().associate { it.value.uuid to it.index }
        val currentMap = currentQueue.withIndex().associate { it.value.uuid to it.index }

        for ((uuid, newIndex) in currentMap) {
            val oldIndex = previousMap[uuid] ?: continue
            if (newIndex > oldIndex) {
                val higherPriorityJoined = previousQueue.getOrNull(oldIndex)?.uuid != currentQueue.getOrNull(oldIndex)?.uuid
                if (higherPriorityJoined) {
                    getPlayerByUUID(uuid)?.sendMessage(
                        Component.text(
                            "\n §6§o⌖ §e§oSomeone with a higher rank has joined the queue.\n §7  §oPurchase one to skip the queue §f§n§ohellz.net/store\n",
                            NamedTextColor.YELLOW
                        )
                    )
                }
            }
        }
    }

    private fun getPlayerByUUID(uuid: UUID): MinestomPlayer? {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
    }

    fun getQueue(queueName: String): String {
        val queue = queues[queueName] ?: return "Queue '$queueName' is empty."
        return queue.toList().mapIndexed { index, player ->
            "${index + 1} - ${player.username}"
        }.joinToString("\n")
    }

    private suspend fun processQueue(queueName: String) {
        if (pausedQueues.contains(queueName)) return

        val queue = queues[queueName] ?: return
        if (queue.isEmpty()) return

        val firstPlayer = queue.peek()
        if (firstPlayer != null) {
            val minestomPlayer = getPlayerByUUID(firstPlayer.uuid)
            if (minestomPlayer != null) {
                velocityChannel.transferPlayer(minestomPlayer, queueName)
                playerJobs[firstPlayer.uuid]?.cancel()
                playerJobs.remove(firstPlayer.uuid)
                queue.poll()

                val nextPlayer = queue.peek()
                if (nextPlayer != null) {
                    getPlayerByUUID(nextPlayer.uuid)?.sendMessage(
                        Component.text("You are now first in line for $queueName.", NamedTextColor.GREEN)
                    )
                }
            }
        }
    }

    fun pauseQueue(queueName: String) {
        pausedQueues.add(queueName)
        queues[queueName]?.forEach { player ->
            getPlayerByUUID(player.uuid)?.sendMessage(
                Component.text("The $queueName queue has been paused.", NamedTextColor.YELLOW)
            )
        }
    }

    suspend fun unpauseQueue(queueName: String) {
        if (pausedQueues.remove(queueName)) {
            queues[queueName]?.forEach { player ->
                getPlayerByUUID(player.uuid)?.sendMessage(
                    Component.text("The $queueName queue has been unpaused.", NamedTextColor.GREEN)
                )
            }
            processQueue(queueName)
        }
    }

    data class Player(val uuid: UUID, val username: String, val rankWeight: Int)
}