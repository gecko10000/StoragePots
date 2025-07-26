package gecko10000.storagepots.guis

import gecko10000.geckolib.extensions.MM
import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckolib.extensions.withDefaults
import gecko10000.geckolib.inventorygui.GUI
import gecko10000.geckolib.inventorygui.InventoryGUI
import gecko10000.geckolib.inventorygui.ItemButton
import gecko10000.storagepots.GUIManager
import gecko10000.storagepots.PotManager
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.inject

class DestroyConfirmationGUI(player: Player, private val pot: Pot) : GUI(player), MyKoinComponent {

    companion object {
        private const val SIZE = 9
    }

    private val potManager: PotManager by inject()
    private val guiManager: GUIManager by inject()

    private fun infoItem(): ItemStack {
        val item = ItemStack.of(Material.TNT)
        item.editMeta {
            it.displayName(parseMM("<red>Destroying this storage pot"))
            it.lore(
                listOf(
                    parseMM("<red>will also destroy the"),
                    MM.deserialize(
                        "<red><u><amount></u> items inside.",
                        Placeholder.unparsed("amount", pot.info.amount.toString())
                    ).withDefaults()
                )
            )
        }
        return item
    }

    private fun cancelButton(): ItemButton {
        val item = ItemStack.of(Material.RED_STAINED_GLASS_PANE)
        item.editMeta {
            it.displayName(parseMM("<red>Cancel"))
        }
        return ItemButton.create(item) { _ ->
            guiManager.open(player, pot)
        }
    }

    private fun confirmButton(): ItemButton {
        val item = ItemStack.of(Material.LIME_STAINED_GLASS_PANE)
        item.editMeta {
            it.displayName(parseMM("<green>Confirm"))
        }
        return ItemButton.create(item) { _ ->
            potManager.`break`(pot)
            player.closeInventory()
        }
    }

    override fun createInventory(): InventoryGUI {
        val gui = InventoryGUI(Bukkit.createInventory(this, InventoryType.HOPPER, parseMM("<dark_red>Are you sure?")))
        gui.addButton(0, cancelButton())
        gui.addButton(1, cancelButton())
        gui.inventory.setItem(2, infoItem())
        gui.addButton(3, confirmButton())
        gui.addButton(4, confirmButton())
        return gui
    }
}
