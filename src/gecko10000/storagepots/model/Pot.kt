package gecko10000.storagepots.model

import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay

data class Pot(
    val block: Block,
    val info: PotInfo,
    val itemDisplay: ItemDisplay,
    val textDisplay: TextDisplay,
)
