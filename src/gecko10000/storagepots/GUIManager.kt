package gecko10000.storagepots

import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.guis.StoragePotGUI
import gecko10000.storagepots.model.Pot
import org.bukkit.block.Block
import org.bukkit.entity.Player

class GUIManager : MyKoinComponent {

    private val openInventories: MutableMap<Block, StoragePotGUI> = mutableMapOf()

    fun open(player: Player, pot: Pot) = openInventories.computeIfAbsent(pot.block) {
        StoragePotGUI(pot)
    }.open(player)

    fun remove(block: Block) = openInventories.remove(block)

    fun update(block: Block) = openInventories[block]?.update()

    fun shutdown() {
        openInventories.values.forEach(StoragePotGUI::destroy)
    }

    fun destroy(block: Block) {
        openInventories[block]?.destroy()
    }

}
