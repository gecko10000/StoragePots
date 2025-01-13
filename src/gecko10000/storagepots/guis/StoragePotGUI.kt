package gecko10000.storagepots.guis

import com.google.common.collect.HashMultimap
import gecko10000.geckolib.GUI
import gecko10000.geckolib.extensions.MM
import gecko10000.geckolib.extensions.isEmpty
import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckolib.extensions.withDefaults
import gecko10000.storagepots.PotManager
import gecko10000.storagepots.StoragePots
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.inject
import redempt.redlib.inventorygui.InventoryGUI
import redempt.redlib.inventorygui.ItemButton
import redempt.redlib.misc.Task
import kotlin.math.ceil
import kotlin.math.min
import kotlin.properties.Delegates
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class StoragePotGUI(player: Player, private var pot: Pot) : GUI(player), MyKoinComponent {

    companion object {
        private const val INPUT_SLOT = 1
        private const val OUTPUT_SLOT = 2
        private const val SIZE = 9
    }

    private val plugin: StoragePots by inject()
    private val potManager: PotManager by inject()

    private val displayRandomizeKey = NamespacedKey(plugin, "random")
    var outputItemCount by Delegates.notNull<Int>()

    private fun upgradeButton(): ItemButton {
        val item = ItemStack.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
        item.editMeta {
            it.displayName(parseMM("<green><b>Upgrade Max Storage"))
            it.lore(buildList {
                add(
                    MM.deserialize(
                        "<dark_green>Exchange rate: <green>1</green> item per <green><amount></green> storage",
                        Placeholder.unparsed("amount", plugin.config.storageUpgradeAmount.toString())
                    ).withDefaults()
                )
                if (!pot.info.isLocked) {
                    add(Component.empty())
                    add(parseMM("<red>Warning: upgrading the storage pot"))
                    add(parseMM("<red>will lock it to the current item"))
                }
            })
            // Hides attributes
            it.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            it.attributeModifiers = HashMultimap.create()
        }
        return ItemButton.create(item) { _ ->
            potManager.upgrade(pot, 1)
            updateInventory()
        }
    }

    // Two cases:
    // - Item swapped into input slot: add to count if it's the right item
    //   (cancel and remove partial if it's too full?)
    // - Item taken out of output slot: ensure nothing is being swapped
    //   in its place. Subtract amount that's taken out.
    private fun handleHotbarSwap(e: InventoryClickEvent) {
        // Item into input slot: e.currentItem is empty,
        // use hotbar key number to get item
        val itemInHotbar = player.inventory.getItem(e.hotbarButton)
        if (e.slot == INPUT_SLOT && !itemInHotbar.isEmpty()) {
            // Either we do nothing if it's the wrong item
            if (pot.info.item == null || !itemInHotbar!!.isSimilar(pot.info.item)) {
                return
            }
            // Or we try to add to the pot and set
            // the item to the leftovers.
            val leftover = potManager.tryAdd(pot, itemInHotbar)
            itemInHotbar.amount = leftover
        }
        if (e.slot == OUTPUT_SLOT) {
            // Swapping with different
            // item not allowed
            if (!itemInHotbar.isEmpty() && !itemInHotbar!!.isSimilar(pot.info.item)) return
            val diff = outputItemCount - (itemInHotbar?.amount ?: 0)
            potManager.remove(pot, diff)
            player.inventory.setItem(e.hotbarButton, pot.info.item!!.asQuantity(outputItemCount))
        }
    }

    // Cases:
    // - Item is placed into input slot. If valid, we try to add it and give back the rest
    // - Item is picked up from output slot. If cursor is empty, we remove it and put item on cursor.
    private fun handleLeftClick(e: InventoryClickEvent) {
        val cursor = e.cursor
        if (e.slot == INPUT_SLOT) {
            if (cursor.isEmpty) return
            val leftover = potManager.tryAdd(pot, cursor)
            player.openInventory.setCursor(cursor.asQuantity(leftover))
            return
        }
        if (e.slot == OUTPUT_SLOT) {
            if (!cursor.isEmpty) return
            potManager.remove(pot, outputItemCount)
            player.openInventory.setCursor(pot.info.item!!.asQuantity(outputItemCount))
        }
    }

    // Almost the same as left click, just slightly different math.
    // Placing: 1 item at a time
    // Picking up: half the stack, rounded up.
    private fun handleRightClick(e: InventoryClickEvent) {
        val cursor = e.cursor
        if (e.slot == INPUT_SLOT) {
            if (cursor.isEmpty) return
            val leftover = potManager.tryAdd(pot, cursor.asOne())
            player.openInventory.setCursor(cursor.asQuantity(cursor.amount - 1 + leftover))
            return
        }
        if (e.slot == OUTPUT_SLOT) {
            if (!cursor.isEmpty) return
            val toPickUp = ceil(outputItemCount / 2.0).toInt()
            potManager.remove(pot, toPickUp)
            player.openInventory.setCursor(pot.info.item!!.asQuantity(toPickUp))
        }
    }

    private val shiftClickOrder: List<Int> = buildList {
        addAll(8 downTo 0)
        addAll(35 downTo 9)
    }

    // Returns the leftovers.
    private fun tryAdd(inv: PlayerInventory, item: ItemStack, slot: Int, onlyOnExisting: Boolean): Int {
        val inSlot = inv.getItem(slot)
        if (!onlyOnExisting && inSlot.isEmpty()) {
            inv.setItem(slot, item.clone())
            return 0
        }
        if (inSlot.isEmpty() || !item.isSimilar(inSlot)) {
            return item.amount
        }
        val transferredAmount = min(inSlot!!.maxStackSize - inSlot.amount, item.amount)
        inSlot.amount += transferredAmount
        return item.amount - transferredAmount
    }

    // First we try to add to existing stacks.
    // Then, we place into empty slots.
    // Am I autistic?
    private fun simulateShiftClickInto(inv: PlayerInventory, item: ItemStack): Int {
        var trackerItem = item
        fun addStep(index: Int, onlyOnExisting: Boolean) {
            if (trackerItem.isEmpty) return
            val leftover = tryAdd(inv, trackerItem, index, onlyOnExisting)
            trackerItem = trackerItem.asQuantity(leftover)
            return
        }
        for (index in shiftClickOrder) {
            addStep(index, true)
        }
        for (index in shiftClickOrder) {
            addStep(index, false)
        }
        return trackerItem.amount
    }

    // Either we shift+click in the GUI, or we shift+click in our inventory.
    // For GUI: we try to add the items to the player's inventory.
    // If there is anything left over, we don't subtract that from the count.
    // For inventory: we try to add to the pot. Leftovers stay behind.
    private fun handleShiftClick(e: InventoryClickEvent) {
        if (e.clickedInventory == null) return
        val isPlayerInventory = e.clickedInventory == player.inventory
        if (isPlayerInventory) {
            val clickedItem = player.inventory.getItem(e.slot) ?: return
            val leftover = potManager.tryAdd(pot, clickedItem)
            clickedItem.amount = leftover
            return
        }
        // Here we are sure it's the GUI
        if (e.slot == OUTPUT_SLOT) {
            val leftover = simulateShiftClickInto(player.inventory, pot.info.item!!.asQuantity(outputItemCount))
            potManager.remove(pot, outputItemCount - leftover)
        }
    }

    // Can swap out of offhand into input,
    // or out of output into offhand.
    private fun handleOffhandSwap(e: InventoryClickEvent) {
        val offhandItem = player.inventory.itemInOffHand
        if (e.slot == INPUT_SLOT && offhandItem.isSimilar(pot.info.item)) {
            val leftover = potManager.tryAdd(pot, offhandItem)
            offhandItem.amount = leftover
            return
        }
        val item = pot.info.item ?: return
        if (e.slot == OUTPUT_SLOT && offhandItem.isEmpty) {
            potManager.remove(pot, outputItemCount)
            player.inventory.setItemInOffHand(item.asQuantity(outputItemCount))
        }
    }

    private fun handleIOClick(e: InventoryClickEvent) {
        e.isCancelled = true
        when (e.click) {
            ClickType.NUMBER_KEY -> handleHotbarSwap(e)
            ClickType.LEFT -> handleLeftClick(e)
            ClickType.RIGHT -> handleRightClick(e)
            ClickType.SHIFT_LEFT -> handleShiftClick(e)
            ClickType.SHIFT_RIGHT -> handleShiftClick(e)
            ClickType.SWAP_OFFHAND -> handleOffhandSwap(e)
            else -> {}
        }
        updateInventory()
    }

    // Maybe if they'd add proper API for changing title...
    private fun changeTitle(newName: Component) {
        player.openInventory.title = LegacyComponentSerializer.legacySection().serialize(newName)
        Task.syncDelayed { -> player.updateInventory() }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun updateInventory(gui: InventoryGUI = this.inventory, isBeforeOpen: Boolean = false) {
        pot = potManager.getPot(pot.block)
        outputItemCount = pot.info.item?.let {
            min(pot.info.amount, it.maxStackSize.toLong()).toInt()
        } ?: 0
        if (!isBeforeOpen) changeTitle(plugin.config.potGUIName(pot.info))

        gui.fill(0, INPUT_SLOT, FILLER)
        gui.fill(OUTPUT_SLOT + 1, SIZE, FILLER)
        val item = pot.info.item
        if (item != null) {
            val displayItem = item.clone()
            displayItem.editMeta {
                it.persistentDataContainer.set(displayRandomizeKey, PersistentDataType.STRING, Uuid.random().toString())
            }
            gui.inventory.setItem(0, displayItem)
        }
        gui.openSlot(INPUT_SLOT)
        gui.inventory.setItem(INPUT_SLOT, null)
        val outputItem = if (pot.info.item != null && outputItemCount > 0) {
            gui.openSlot(OUTPUT_SLOT)
            pot.info.item?.asQuantity(outputItemCount)
        } else {
            gui.closeSlot(OUTPUT_SLOT)
            ItemStack.of(Material.BARRIER).also {
                it.editMeta {
                    it.displayName(
                        parseMM("<red>No item")
                    )
                }
            }
        }
        gui.inventory.setItem(OUTPUT_SLOT, outputItem)
        gui.setReturnsItems(false)

        gui.addButton(4, upgradeButton())
    }

    override fun createInventory(): InventoryGUI {
        val gui = InventoryGUI(Bukkit.createInventory(this, SIZE, plugin.config.potGUIName(pot.info)))
        gui.setOnClickOpenSlot { e -> handleIOClick(e) }
        gui.setOnDragOpenSlot { e -> e.isCancelled = true }
        updateInventory(gui, isBeforeOpen = true)
        return gui
    }
}
