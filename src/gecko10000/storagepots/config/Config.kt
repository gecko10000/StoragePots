@file:UseSerializers(MMComponentSerializer::class)

package gecko10000.storagepots.config

import gecko10000.geckolib.config.serializers.MMComponentSerializer
import gecko10000.geckolib.extensions.parseMM
import gecko10000.storagepots.model.PotInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.kyori.adventure.text.Component

@Serializable
data class Config(
    val autosaveIntervalSeconds: Int = 300,
    val potName: Component = parseMM("<gold><b>Storage Pot"),
    private val fullPotLore: List<Component> = listOf(
        parseMM("<yellow>Storing <amount>/<max> <item>"),
        Component.empty(),
        parseMM("<aqua>Place to activate.")
    ),
    private val emptyPotLore: List<Component> = listOf(
        parseMM("<green>Empty (<max> storage)"),
        Component.empty(),
        parseMM("<aqua>Place to activate.")
    ),
    private val potGUIName: Component = parseMM("<dark_blue>Storage Pot: <amount>/<max>"),
    val defaultMaxAmount: Long = 1000,
    val storageUpgradeAmount: Int = 10,
) {
    fun potLore(potInfo: PotInfo): List<Component> {
        val lore = if (potInfo.item == null) emptyPotLore else fullPotLore
        return lore.map { line ->
            val withAmounts = line.replaceText {
                it.matchLiteral("<amount>").replacement(potInfo.amount.toString())
            }.replaceText {
                it.matchLiteral("<max>").replacement(potInfo.maxAmount.toString())
            }
            if (potInfo.item != null) {
                withAmounts.replaceText {
                    it.matchLiteral("<item>").replacement(potInfo.item.effectiveName())
                }
            } else withAmounts
        }
    }

    fun potGUIName(info: PotInfo): Component {
        return potGUIName.replaceText {
            it.matchLiteral("<amount>").replacement(info.amount.toString())
        }.replaceText {
            it.matchLiteral("<max>").replacement(info.maxAmount.toString())
        }
    }
}
