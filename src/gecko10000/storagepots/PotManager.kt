package gecko10000.storagepots

import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.guis.StoragePotGUI
import gecko10000.storagepots.model.Pot
import gecko10000.storagepots.model.PotInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.DecoratedPot
import org.bukkit.event.EventPriority
import org.bukkit.event.block.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.koin.core.component.inject
import redempt.redlib.misc.EventListener
import redempt.redlib.misc.Task
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class PotManager : MyKoinComponent {

    private val plugin: StoragePots by inject()
    private val json: Json by inject()

    private val potKey = NamespacedKey(plugin, "pot")
    private val loadedPots: MutableMap<Block, Pot> = mutableMapOf()

    init {
        Bukkit.getWorlds()
            .flatMap { it.loadedChunks.toList() }
            .forEach(this::loadChunk)
        EventListener(ChunkLoadEvent::class.java) { e -> loadChunk(e.chunk) }
        EventListener(ChunkUnloadEvent::class.java) { e -> unloadChunk(e.chunk) }
        EventListener(BlockPlaceEvent::class.java) { e ->
            this.place(e.itemInHand, e.block)
        }
        EventListener(BlockBreakEvent::class.java) { e ->
            val pot = loadedPots[e.block] ?: return@EventListener
            e.isCancelled = true
            val inPot = pot.info.item ?: return@EventListener
            val amountToDrop = min(pot.info.amount, (inPot.maxStackSize).toLong()).toInt()
            if (amountToDrop == 0) return@EventListener
            remove(pot, amountToDrop)
            e.block.world.dropItem(e.block.location.add(0.5, 1.0, 0.5), inPot.asQuantity(amountToDrop)) {
                it.velocity = Vector(0, 0, 0)
            }
        }
        EventListener(PlayerInteractEvent::class.java, EventPriority.HIGHEST) { e ->
            if (e.action != Action.RIGHT_CLICK_BLOCK) return@EventListener
            val pot = loadedPots[e.clickedBlock] ?: return@EventListener
            val player = e.player
            if (player.isSneaking) return@EventListener
            e.isCancelled = true
            // Open GUI if:
            // - hand is empty
            // - storage is full
            // - item in hand differs from storage pot's
            val itemInHand = player.inventory.itemInMainHand
            if (itemInHand.isEmpty) {
                StoragePotGUI(player, pot)
                return@EventListener
            }
            val item = pot.info.item ?: itemInHand.asQuantity(1)
            val availableStorage = pot.info.maxAmount - pot.info.amount
            if (availableStorage >= 0 && itemInHand.isSimilar(item)) {
                val remaining = tryAdd(pot, itemInHand)
                itemInHand.amount = remaining
            } else {
                StoragePotGUI(player, pot)
            }
        }
        EventListener(BlockPistonExtendEvent::class.java) { e -> handlePiston(e, e.blocks) }
        EventListener(BlockPistonRetractEvent::class.java) { e -> handlePiston(e, e.blocks) }

        Task.syncRepeating({ ->
            loadedPots.values.forEach(this::savePot)
        }, 0L, plugin.config.autosaveIntervalSeconds * 20L)
    }

    private fun handlePiston(e: BlockPistonEvent, blocks: List<Block>) {
        if (blocks.any { it in loadedPots }) {
            e.isCancelled = true
        }
    }

    private fun place(item: ItemStack, block: Block) {
        val infoString = item.persistentDataContainer.get(potKey, PersistentDataType.STRING) ?: return
        val potInfo = json.decodeFromString<PotInfo>(infoString)
        val pot = Pot(block, potInfo)
        loadedPots[block] = pot
        savePot(pot)
    }

    private fun `break`(e: BlockDropItemEvent) {
        val pot = loadedPots.remove(e.block) ?: return
        val itemStack = potItem(pot.info)
        val item = e.items.firstOrNull() ?: return
        item.itemStack = itemStack
        e.items.clear()
        e.items.add(item)
    }

    private fun loadChunk(chunk: Chunk) {
        chunk.getTileEntities({ it.type == Material.DECORATED_POT }, false)
            .filterIsInstance<DecoratedPot>()
            .forEach(this::loadPot)
    }

    private fun loadPot(pot: DecoratedPot) {
        val infoString = pot.persistentDataContainer.get(potKey, PersistentDataType.STRING)
        infoString ?: return // Not a storage pot, ignore.
        val info = json.decodeFromString<PotInfo>(infoString)
        val block = pot.block
        loadedPots[block] = Pot(block, info)
    }

    private fun unloadChunk(chunk: Chunk) {
        loadedPots.keys
            .filter { it.chunk == chunk }
            .forEach {
                val pot = loadedPots.remove(it)
                if (pot != null) {
                    savePot(pot)
                }
            }
    }

    // Returns the amount of items
    // left over from trying to add.
    fun tryAdd(pot: Pot, item: ItemStack): Int {
        if (item.isEmpty) return 0
        var info = pot.info
        if (info.item == null) {
            info = info.copy(item = item.asQuantity(1), amount = item.amount.toLong())
            updatePot(pot, info)
            return 0
        }
        if (!item.isSimilar(info.item)) {
            return item.amount
        }
        var availableSpace = info.maxAmount - info.amount
        val neededSpace = item.amount - availableSpace
        if (neededSpace > 0 && info.isAutoUpgrading) {
            // Perform upgrade
            val upgradeIncrease = plugin.config.storageUpgradeAmount
            val diffFromUpgrade = upgradeIncrease + 1
            val numUpgrades = ceil(neededSpace / diffFromUpgrade.toDouble()).toInt()
            info = info.copy(
                amount = info.amount - numUpgrades,
                maxAmount = info.maxAmount + numUpgrades * upgradeIncrease,
                isLocked = true,
            )
            availableSpace = info.maxAmount
        }
        val toInsert = min(item.amount.toLong(), availableSpace)
        info = info.copy(
            amount = info.amount + toInsert,
        )
        updatePot(pot, info)
        return item.amount - toInsert.toInt()
    }

    fun remove(pot: Pot, amount: Int) {
        val newAmount = max(pot.info.amount - amount, 0)
        val item = if (newAmount == 0L && !pot.info.isLocked) null else pot.info.item
        val newInfo = pot.info.copy(amount = newAmount, item = item)
        updatePot(pot, newInfo)
    }

    fun upgrade(pot: Pot, numTimes: Int) {
        val newInfo = pot.info.copy(
            maxAmount = pot.info.maxAmount + plugin.config.storageUpgradeAmount * numTimes,
            amount = pot.info.amount - numTimes,
            isLocked = true
        )
        updatePot(pot, newInfo)
    }

    private fun updatePot(pot: Pot, newInfo: PotInfo): Pot {
        val newPot = pot.copy(info = newInfo)
        loadedPots[newPot.block] = newPot
        return newPot
    }

    private fun savePot(pot: Pot) {
        val decoratedPot = pot.block.state as? DecoratedPot
        if (decoratedPot == null) {
            plugin.logger.warning("Pot at ${pot.block.location} could not be retrieved as a DecoratedPot")
            return
        }
        val data = json.encodeToString(pot.info)
        decoratedPot.persistentDataContainer.set(potKey, PersistentDataType.STRING, data)
        decoratedPot.update()
    }

    fun saveAll() {
        loadedPots.values.forEach(this::savePot)
    }

    fun getPot(block: Block): Pot = loadedPots.getValue(block)

    fun potItem(info: PotInfo): ItemStack {
        val dataString = json.encodeToString(info)
        val item = ItemStack.of(Material.DECORATED_POT)
        item.editMeta {
            it.displayName(plugin.config.potName)
            it.lore(plugin.config.potLore(info))
            it.persistentDataContainer.set(potKey, PersistentDataType.STRING, dataString)
        }
        return item
    }

}
