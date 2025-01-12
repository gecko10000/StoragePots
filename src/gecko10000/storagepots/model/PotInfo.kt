@file:UseSerializers(InternalItemStackSerializer::class)

package gecko10000.storagepots.model

import gecko10000.geckolib.config.serializers.InternalItemStackSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bukkit.inventory.ItemStack

@Serializable
data class PotInfo(
    val item: ItemStack?,
    val amount: Long,
    val maxAmount: Long,
    val isLocked: Boolean,
    val isAutoUpgrading: Boolean,
)
