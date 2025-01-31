package gecko10000.storagepots

import gecko10000.geckolib.extensions.isEmpty
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import org.bukkit.Material
import org.bukkit.block.*
import org.bukkit.block.data.Directional
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.Inventory
import org.koin.core.component.inject
import redempt.redlib.misc.EventListener
import redempt.redlib.misc.Task
import kotlin.math.min

class ExternalInvListener : MyKoinComponent {

    private val plugin: StoragePots by inject()
    private val potManager: PotManager by inject()

    private fun tryInput(hopper: Hopper, pot: Pot) {
        var remaining = plugin.config.hopperTransferAmount
        val inv = hopper.inventory
        for (slot in inv.contents.indices) {
            if (remaining <= 0) return
            val item = inv.getItem(slot)
            if (item.isEmpty()) continue
            val toTransfer = item!!.asQuantity(min(item.amount, remaining))
            val leftover = potManager.tryAdd(pot, toTransfer)
            val successfullyMoved = toTransfer.amount - leftover
            item.amount -= successfullyMoved
            remaining -= successfullyMoved
        }
    }

    private fun tryOutput(pot: Pot, hopper: Hopper) {
        val item = pot.info.item ?: return
        val outputAmount = min(plugin.config.hopperTransferAmount.toLong(), pot.info.amount).toInt()
        val leftover = hopper.inventory.addItem(item).values.firstOrNull()?.amount ?: 0
        potManager.remove(pot, outputAmount - leftover)
    }

    private fun validateHopper(block: Block, direction: BlockFace): Block? {
        val relative = block.getRelative(direction)
        val relativeData = relative.blockData as? org.bukkit.block.data.type.Hopper ?: return null
        if (relativeData.facing != direction.oppositeFace) return null
        return relative
    }

    private val incomingHopperFaces = (Material.HOPPER.createBlockData() as org.bukkit.block.data.type.Hopper)
        .faces
        .map(BlockFace::getOppositeFace)
        .toSet()

    private fun getIncomingHoppers(block: Block): Set<Block> {
        return incomingHopperFaces
            .mapNotNull { validateHopper(block, it) }
            .toSet()
    }

    fun hopperTick(pot: Pot) {
        val hoppers = getIncomingHoppers(pot.block)
        hoppers.forEach { tryInput(it.getState(false) as Hopper, pot) }
        val under = pot.block.getRelative(BlockFace.DOWN).getState(false) as? Hopper ?: return
        tryOutput(pot, under)
    }

    private fun isStoragePotInventory(inventory: Inventory): Boolean {
        val pot = inventory.getHolder(false) as? DecoratedPot ?: return false
        return potManager.getPot(pot.block) != null
    }

    private fun handleDropper(dropper: Dropper, e: InventoryMoveItemEvent) {
        val direction = (dropper.blockData as Directional).facing
        val destination = dropper.block.getRelative(direction)
        // Facing into decorated pot
        if (destination.type != Material.DECORATED_POT) return
        // Which is a storage pot
        val pot = potManager.getPot(destination) ?: return
        val itemFits = pot.info.item == null || e.item.isSimilar(pot.info.item)
        if (!itemFits || !potManager.hasRoom(pot, e.item.amount)) {
            e.isCancelled = true
            return
        }
        // We don't cancel. Instead, we set the item amount to 0.
        // This way, item is removed from dropper but doesn't go
        // into the decorated pot.
        val leftover = potManager.tryAdd(pot, e.item)
        if (leftover > 0) {
            val loc = destination.location
            val leftoverItem = e.item.asQuantity(leftover)
            plugin.logger.warning("Pot at $loc had room but gave leftovers $leftoverItem")
        }
        e.item.amount = 0
    }

    init {
        Task.syncRepeating({ ->
            potManager.getAll().forEach(this::hopperTick)
        }, 0L, plugin.config.hopperTransferCooldown.toLong())
        EventListener(InventoryMoveItemEvent::class.java) { e ->
            val holder = e.source.getHolder(false)
            if (holder is Dropper) {
                handleDropper(holder, e)
                return@EventListener
            }
            if (isStoragePotInventory(e.source) || isStoragePotInventory(e.destination)) {
                e.isCancelled = true
            }
        }
    }

}
