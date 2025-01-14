package gecko10000.storagepots

import gecko10000.geckolib.extensions.isEmpty
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.DecoratedPot
import org.bukkit.block.Hopper
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
        if (hoppers.isEmpty()) return
        hoppers.forEach { tryInput(it.getState(false) as Hopper, pot) }
        val under = pot.block.getRelative(BlockFace.DOWN).getState(false) as? Hopper ?: return
        tryOutput(pot, under)
    }

    fun isStoragePotInventory(inventory: Inventory): Boolean {
        val pot = inventory.getHolder(false) as? DecoratedPot ?: return false
        return potManager.getPot(pot.block) != null
    }

    init {
        Task.syncRepeating({ ->
            potManager.getAll().forEach(this::hopperTick)
        }, 0L, plugin.config.hopperTransferCooldown.toLong())
        EventListener(InventoryMoveItemEvent::class.java) { e ->
            if (isStoragePotInventory(e.source) || isStoragePotInventory(e.destination)) {
                e.isCancelled = true
            }
        }
    }

}
