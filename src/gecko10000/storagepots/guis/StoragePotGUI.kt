package gecko10000.storagepots.guis

import com.google.common.collect.HashMultimap
import gecko10000.geckolib.GUI
import gecko10000.geckolib.extensions.MM
import gecko10000.geckolib.extensions.parseMM
import gecko10000.storagepots.StoragePots
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.Pot
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.inject
import redempt.redlib.inventorygui.InventoryGUI
import redempt.redlib.inventorygui.ItemButton

class StoragePotGUI(player: Player, private var pot: Pot) : GUI(player), MyKoinComponent {

    companion object {
        private const val SIZE = 9
    }

    private val plugin: StoragePots by inject()

    private fun upgradeButton(): ItemButton {
        val item = ItemStack.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
        item.editMeta {
            it.displayName(parseMM("<green>Upgrade Max Storage"))
            it.lore(buildList {
                add(MM.deserialize("<darkgreen>Cost: <cost> items"))
                if (!pot.info.isLocked) {
                    add(parseMM("<red>Warning: upgrading the storage pot"))
                    add(parseMM("<red>will lock it to the current item"))
                }
            })
            // Hides attributes
            it.attributeModifiers = HashMultimap.create()
        }
        return ItemButton.create(item) { e ->
            println("yup")
        }
    }

    private fun updateInventory(gui: InventoryGUI = this.inventory) {
        gui.fill(0, SIZE, FILLER.let {
            it.editMeta {
                it.displayName(
                    MM.deserialize(
                        "<gray><amount>/<max>",
                        Placeholder.unparsed("amount", pot.info.amount.toString()),
                        Placeholder.unparsed("max", pot.info.maxAmount.toString()),
                    )
                )
            }
            it
        })
        gui.addButton(4, upgradeButton())
    }

    override fun createInventory(): InventoryGUI {
        val gui = InventoryGUI(Bukkit.createInventory(this, SIZE))
        updateInventory(gui)
        return gui
    }
}
