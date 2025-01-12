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
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.inject
import redempt.redlib.inventorygui.InventoryGUI
import redempt.redlib.inventorygui.ItemButton
import redempt.redlib.misc.Task
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
        e.isCancelled = true
        val itemInHotbar = player.inventory.getItem(e.hotbarButton)
        if (e.slot == INPUT_SLOT && !itemInHotbar.isEmpty()) {
            if (pot.info.item == null) {
                val leftover = potManager.tryAdd(pot, itemInHotbar!!)
                itemInHotbar.amount = leftover
            }
            // Either we do nothing if it's the wrong item
            if (!itemInHotbar!!.isSimilar(pot.info.item)) {
                return
            }
            // Or we try to add to the pot and set
            // the item to the leftovers.
            itemInHotbar.amount = potManager.tryAdd(pot, itemInHotbar)
            return
        }
        if (e.slot == OUTPUT_SLOT) {
            if (outputItemCount == 0) return
            // Swapping with different
            // item not allowed
            if (!itemInHotbar.isEmpty() && !itemInHotbar!!.isSimilar(pot.info.item)) return
            val diff = outputItemCount - (itemInHotbar?.amount ?: 0)
            potManager.remove(pot, diff)
            player.inventory.setItem(e.hotbarButton, pot.info.item!!.asQuantity(outputItemCount))
        }
    }

    private fun handleIOClick(e: InventoryClickEvent) {
        println("${e.click}, ${e.action}")
        // TODO: handle every action manually. Hooray!
        // TODO: Luckily, we should only need to handle
        // TODO: hotbar swaps, shift+clicks, and normal clicks.
        // TODO: Remember, this is already filtered to only open
        // TODO: slot clicks.
        when (e.click) {
            ClickType.NUMBER_KEY -> {
                handleHotbarSwap(e)
            }

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
        println(pot.info)
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
        gui.openSlot(OUTPUT_SLOT)
        gui.setReturnsItems(false)
        gui.inventory.setItem(INPUT_SLOT, null)
        val outputItem = if (pot.info.item != null && outputItemCount > 0)
            pot.info.item?.asQuantity(outputItemCount)
        else
            ItemStack.of(Material.BARRIER).also {
                it.editMeta {
                    it.displayName(
                        parseMM("<red>No item")
                    )
                }
            }
        gui.inventory.setItem(OUTPUT_SLOT, outputItem)

        gui.addButton(4, upgradeButton())
    }

    override fun createInventory(): InventoryGUI {
        val gui = InventoryGUI(Bukkit.createInventory(this, SIZE, plugin.config.potGUIName(pot.info)))
        gui.setOnClickOpenSlot { e -> handleIOClick(e) }
        updateInventory(gui, isBeforeOpen = true)
        return gui
    }
}
