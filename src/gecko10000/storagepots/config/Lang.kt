@file:UseSerializers(MMComponentSerializer::class)

package gecko10000.storagepots.config

import gecko10000.geckolib.config.serializers.MMComponentSerializer
import gecko10000.geckolib.extensions.name
import gecko10000.geckolib.extensions.parseMM
import gecko10000.storagepots.model.PotInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.kyori.adventure.text.Component

@Serializable
data class Lang(
    val potItemName: Component = parseMM("<gold><b>Storage Pot"),
    private val fullPotItemLore: List<Component> = listOf(
        parseMM("<yellow>Stores <max> <item>"),
        Component.empty(),
        parseMM("<aqua>Place to activate.")
    ),
    private val emptyPotItemLore: List<Component> = listOf(
        parseMM("<green>Stores <max> items"),
        Component.empty(),
        parseMM("<aqua>Place to activate.")
    ),
    private val potGUIName: Component = parseMM("<dark_blue>Storage Pot: <amount>/<max>"),
    val noPermissionMessage: Component = parseMM("<red>You don't have permission to toggle this!")
) {
    fun potItemLore(potInfo: PotInfo): List<Component> {
        val lore = if (potInfo.item == null) emptyPotItemLore else fullPotItemLore
        return lore.map { line ->
            val withAmounts = line.replaceText {
                it.matchLiteral("<amount>").replacement(potInfo.amount.toString())
            }.replaceText {
                it.matchLiteral("<max>").replacement(potInfo.maxAmount.toString())
            }
            if (potInfo.item != null) {
                withAmounts.replaceText {
                    it.matchLiteral("<item>").replacement(potInfo.item.name())
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
