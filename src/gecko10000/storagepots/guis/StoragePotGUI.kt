package gecko10000.storagepots.guis

import com.google.common.collect.HashMultimap
import gecko10000.geckolib.GUI
import gecko10000.geckolib.extensions.MM
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
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.koin.core.component.inject
import redempt.redlib.inventorygui.InventoryGUI
import redempt.redlib.inventorygui.ItemButton
import redempt.redlib.misc.Task
import kotlin.math.min

class StoragePotGUI(player: Player, private val pot: Pot) : GUI(player), MyKoinComponent {

    companion object {
        private const val INPUT_SLOT = 1
        private const val OUTPUT_SLOT = 2
        private const val SIZE = 9
    }

    private val plugin: StoragePots by inject()
    private val potManager: PotManager by inject()

    private val outputItemCount = pot.info.item?.let {
        min(pot.info.amount, it.maxStackSize.toLong()).toInt()
    } ?: 0

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
        return ItemButton.create(item) { e ->
            println("yup")
        }
    }

    private fun handleIOClick(e: InventoryClickEvent) {
        println("${e.click}, ${e.action}")
        // TODO: handle every action manually. Hooray!
        // TODO: Luckily, we should only need to handle
        // TODO: hotbar swaps, shift+clicks, and normal clicks.
        // TODO: Remember, this is already filtered to only open
        // TODO: slot clicks.
        player.openInventory.title =
            LegacyComponentSerializer.legacySection().serialize(plugin.config.potGUIName(pot.info))
        Task.syncDelayed { -> player.updateInventory() }
    }

    override fun createInventory(): InventoryGUI {
        val info = pot.info
        val gui = InventoryGUI(Bukkit.createInventory(this, SIZE, plugin.config.potGUIName(info)))
        gui.fill(0, SIZE, FILLER)
        gui.openSlot(INPUT_SLOT)
        gui.openSlot(OUTPUT_SLOT)
        gui.inventory.setItem(INPUT_SLOT, null)
        gui.inventory.setItem(OUTPUT_SLOT, info.item?.asQuantity(outputItemCount))
        gui.setReturnsItems(false)
        gui.setOnClickOpenSlot { e ->
            handleIOClick(e)
            /*if (info.item == null) {
                // TODO: Set the item in whichever slot
                return@setOnClickOpenSlot
            }
            val inputAmount = gui.inv?.amount ?: 0
            println("$inputAmount, $outputAmount")
            val diff = outputItemCount - inputAmount - outputAmount
            val newPot = potManager.updatePot(pot, info.copy(amount = info.amount - diff))*/
        }

        gui.addButton(4, upgradeButton())
        return gui
    }
}
