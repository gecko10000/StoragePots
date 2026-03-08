@file:UseSerializers(InternalItemStackSerializer::class, UUIDSerializer::class)

package gecko10000.storagepots.model

import gecko10000.geckolib.config.serializers.InternalItemStackSerializer
import gecko10000.geckolib.config.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bukkit.inventory.ItemStack

@Serializable
data class PotInfo(
    val item: ItemStack?,
    val amount: Long,
    val maxAmount: Long,
    val isLocked: Boolean = false,
    val isAutoUpgrading: Boolean,
    val isSellButtonEnabled: Boolean = true,
)
