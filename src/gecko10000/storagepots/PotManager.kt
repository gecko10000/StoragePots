package gecko10000.storagepots

import gecko10000.geckolib.extensions.MM
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import gecko10000.storagepots.model.PotInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.DecoratedPot
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.block.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.inject
import redempt.redlib.misc.EventListener
import redempt.redlib.misc.Task
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PotManager : MyKoinComponent {

    private val plugin: StoragePots by inject()
    private val guiManager: GUIManager by inject()
    private val json: Json by inject()

    private val potKey = NamespacedKey(plugin, "pot")
    private val loadedPots: MutableMap<Block, Pot> = mutableMapOf()

    init {
        Bukkit.getWorlds()
            .flatMap { it.loadedChunks.toList() }
            .forEach(this::loadChunk)
        EventListener(ChunkLoadEvent::class.java) { e -> loadChunk(e.chunk) }
        EventListener(ChunkUnloadEvent::class.java) { e -> unloadChunk(e.chunk) }
        EventListener(BlockPlaceEvent::class.java, EventPriority.MONITOR) { e ->
            if (e.isCancelled) return@EventListener
            this.place(e.itemInHand, e.block)
        }
        EventListener(BlockBreakEvent::class.java, EventPriority.HIGHEST) { e ->
            if (e.isCancelled) return@EventListener
            val pot = loadedPots[e.block] ?: return@EventListener
            e.isCancelled = true
            breakDropStack(pot)
        }
        EventListener(PlayerInteractEvent::class.java, EventPriority.HIGHEST) { e ->
            if (e.useInteractedBlock() == Event.Result.DENY) return@EventListener
            if (e.action != Action.RIGHT_CLICK_BLOCK) return@EventListener
            val pot = loadedPots[e.clickedBlock] ?: return@EventListener
            val player = e.player
            if (player.isSneaking) return@EventListener
            e.isCancelled = true
            guiManager.open(player, pot)
        }
        EventListener(BlockPistonExtendEvent::class.java, EventPriority.LOWEST) { e -> handlePiston(e, e.blocks) }
        EventListener(BlockPistonRetractEvent::class.java, EventPriority.LOWEST) { e -> handlePiston(e, e.blocks) }

        Task.syncRepeating({ ->
            loadedPots.values.forEach(this::savePot)
        }, 0L, plugin.config.autosaveIntervalSeconds * 20L)
    }

    private fun breakDropStack(pot: Pot) {
        val inPot = pot.info.item ?: return
        val amountToDrop = min(pot.info.amount, (inPot.maxStackSize).toLong()).toInt()
        if (amountToDrop == 0) return
        remove(pot, amountToDrop)
        pot.block.world.dropItem(pot.block.location.add(0.5, 1.0, 0.5), inPot.asQuantity(amountToDrop)) {
            it.velocity = Vector(0, 0, 0)
        }
    }

    private fun handlePiston(e: BlockPistonEvent, blocks: List<Block>) {
        if (blocks.any { it in loadedPots }) {
            e.isCancelled = true
        }
    }

    private fun spawnItemDisplay(block: Block, info: PotInfo): ItemDisplay {
        val display = block.world.spawn(block.location.add(0.5, 1.26, 0.5), ItemDisplay::class.java) {
            it.isPersistent = false
            it.transformation = Transformation(
                Vector3f(0f),
                Quaternionf(sqrt(2f) / 2, 0f, 0f, sqrt(2f) / 2),
                Vector3f(0.25f),
                Quaternionf()
            )
        }
        display.update(info)
        return display
    }

    private fun spawnTextDisplay(block: Block, info: PotInfo): TextDisplay {
        val display = block.world.spawn(block.location.add(0.5, 1.4, 0.5), TextDisplay::class.java) {
            it.isPersistent = false
            it.billboard = Display.Billboard.CENTER
            it.isShadowed = true
            it.backgroundColor = Color.fromARGB(0, 0, 0, 0)
        }
        display.update(info)
        return display
    }

    private fun ItemDisplay.update(info: PotInfo) {
        this.isInvisible = info.amount == 0L && !info.isLocked
        this.setItemStack(info.item)
    }

    private fun TextDisplay.update(info: PotInfo) {
        val invisible = info.amount == 0L && !info.isLocked
        if (invisible)
            this.text(null)
        else
            this.text(
                MM.deserialize(
                    "<b><amount>",
                    Placeholder.unparsed("amount", info.amount.toString()),
                    //Placeholder.component("item", item.name()),
                )
            )
    }

    private fun place(item: ItemStack, block: Block) {
        val infoString = item.persistentDataContainer.get(potKey, PersistentDataType.STRING) ?: return
        val potInfo = json.decodeFromString<PotInfo>(infoString)
        val pot = Pot(block, potInfo, spawnItemDisplay(block, potInfo), spawnTextDisplay(block, potInfo))
        loadedPots[block] = pot
        savePot(pot)
    }

    fun `break`(pot: Pot) {
        val pot = loadedPots.remove(pot.block) ?: return
        pot.itemDisplay.remove()
        pot.textDisplay.remove()
        breakDropStack(pot)
        val item = if (pot.info.isLocked) pot.info.item else null
        val potItemInfo = pot.info.copy(
            item = item,
            amount = 0,
        )
        pot.block.type = Material.AIR
        pot.block.world.dropItem(pot.block.location.toCenterLocation(), potItem(potItemInfo)).velocity = Vector()
        guiManager.destroy(pot.block)
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
        loadedPots[block] = Pot(block, info, spawnItemDisplay(block, info), spawnTextDisplay(block, info))
    }

    private fun unloadChunk(chunk: Chunk) {
        loadedPots.keys
            .filter { it.chunk == chunk }
            .forEach {
                val pot = loadedPots.remove(it)
                if (pot != null) {
                    pot.itemDisplay.remove()
                    savePot(pot)
                }
            }
    }

    // Definitely has room if auto upgrading, might have room otherwise (need to check)
    fun hasRoom(pot: Pot, amount: Int) = pot.info.isAutoUpgrading || pot.info.amount + amount <= pot.info.maxAmount

    fun isBlacklistedItem(item: ItemStack): Boolean {
        val itemName = item.type.name
        return itemName.endsWith("SHULKER_BOX") || itemName.endsWith("BUNDLE") || item.persistentDataContainer.has(
            potKey
        )
    }

    // Returns the amount of items
    // left over from trying to add.
    fun tryAdd(pot: Pot, item: ItemStack, updateGUI: Boolean = true): Int {
        val pot = loadedPots[pot.block] ?: return item.amount
        if (item.isEmpty) return 0
        if (isBlacklistedItem(item)) {
            return item.amount
        }
        var info = pot.info
        if (info.item == null) {
            info = info.copy(item = item.asQuantity(1))
        }
        if (!item.isSimilar(info.item)) {
            return item.amount
        }
        val item = item.clone()
        var availableSpace = info.maxAmount - info.amount
        val neededSpace = item.amount - availableSpace
        if (neededSpace > 0 && info.isAutoUpgrading) {
            // Perform upgrade
            val upgradeIncrease = plugin.config.storageUpgradeAmount
            val diffFromUpgrade = upgradeIncrease + 1
            val numUpgrades = ceil(neededSpace / diffFromUpgrade.toDouble()).toInt()

            item.amount -= numUpgrades
            info = info.copy(
                maxAmount = info.maxAmount + numUpgrades * upgradeIncrease,
                isLocked = true,
            )
            availableSpace = info.maxAmount - info.amount
        }
        val toInsert = min(item.amount.toLong(), availableSpace)
        info = info.copy(
            amount = info.amount + toInsert,
        )
        updatePot(pot, info, updateGUI)
        return item.amount - toInsert.toInt()
    }

    fun remove(pot: Pot, amount: Int, updateGUI: Boolean = true) {
        val pot = loadedPots[pot.block] ?: return
        val newAmount = max(pot.info.amount - amount, 0)
        val item = if (newAmount == 0L && !pot.info.isLocked) null else pot.info.item?.clone()
        val newInfo = pot.info.copy(amount = newAmount, item = item)
        updatePot(pot, newInfo, updateGUI)
    }

    fun upgrade(pot: Pot, numTimes: Int) {
        val upgrades = min(numTimes.toLong(), pot.info.amount)
        if (upgrades == 0L) return
        val newInfo = pot.info.copy(
            maxAmount = pot.info.maxAmount + plugin.config.storageUpgradeAmount * upgrades,
            amount = pot.info.amount - upgrades,
            isLocked = true
        )
        updatePot(pot, newInfo)
    }

    fun toggleAutoUpgrades(pot: Pot) {
        updatePot(pot, pot.info.copy(isAutoUpgrading = !pot.info.isAutoUpgrading))
    }

    private fun updatePot(pot: Pot, newInfo: PotInfo, updateGUI: Boolean = true): Pot {
        val newPot = pot.copy(info = newInfo)
        loadedPots[newPot.block] = newPot

        if (updateGUI) {
            guiManager.update(newPot.block)
        }
        newPot.itemDisplay.update(newInfo)
        newPot.textDisplay.update(newInfo)
        return newPot
    }

    private fun savePot(pot: Pot) {
        val decoratedPot = pot.block.getState(false) as? DecoratedPot
        if (decoratedPot == null) {
            plugin.logger.warning("Pot at ${pot.block.location} could not be retrieved as a DecoratedPot and failed to save.")
            return
        }
        val data = json.encodeToString(pot.info)
        decoratedPot.persistentDataContainer.set(potKey, PersistentDataType.STRING, data)
        decoratedPot.update()
    }

    fun saveAndClear() {
        loadedPots.values.forEach(this::savePot)
        loadedPots.values.forEach {
            it.itemDisplay.remove()
            it.textDisplay.remove()
        }
        loadedPots.clear()
    }

    fun getPot(block: Block): Pot? = loadedPots[block]

    fun getAll(): Set<Pot> = loadedPots.values.toSet()

    fun potItem(info: PotInfo): ItemStack {
        val dataString = json.encodeToString(info)
        val item = ItemStack.of(Material.DECORATED_POT)
        item.editMeta {
            it.displayName(plugin.lang.potItemName)
            it.lore(plugin.lang.potItemLore(info))
            it.persistentDataContainer.set(potKey, PersistentDataType.STRING, dataString)
        }
        return item
    }

}
