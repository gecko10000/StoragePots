@file:UseSerializers(MMComponentSerializer::class)

package gecko10000.storagepots.config

import gecko10000.geckolib.config.serializers.MMComponentSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Config(
    val autosaveIntervalSeconds: Int = 300,
    val defaultMaxAmount: Long = 1000,
    val defaultAutoUpgrade: Boolean = false,
    val storageUpgradeAmount: Int = 10,
    val hopperTransferAmount: Int = 1,
    val hopperTransferCooldown: Int = 8,
)
