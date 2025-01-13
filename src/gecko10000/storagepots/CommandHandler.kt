package gecko10000.storagepots

import gecko10000.geckolib.extensions.parseMM
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.PotInfo
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.inject
import redempt.redlib.commandmanager.CommandHook
import redempt.redlib.commandmanager.CommandParser
import redempt.redlib.itemutils.ItemUtils

class CommandHandler : MyKoinComponent {

    private val plugin: StoragePots by inject()
    private val potManager: PotManager by inject()

    init {
        CommandParser(plugin.getResource("command.rdcml")).parse().register("mm", this)
    }

    @CommandHook("reload")
    fun reload(sender: CommandSender) {
        plugin.reloadConfigs()
        sender.sendMessage(parseMM("<green>Configs reloaded."))
    }

    @CommandHook("give")
    fun give(sender: CommandSender, target: Player) {
        ItemUtils.give(
            target, potManager.potItem(
                PotInfo(
                    item = null,
                    amount = 0,
                    maxAmount = plugin.config.defaultMaxAmount,
                    isLocked = false,
                    isAutoUpgrading = plugin.config.defaultAutoUpgrade,
                )
            )
        )
        sender.sendMessage(parseMM("<green>Gave ${target.name} a storage pot."))
    }

}
