package gecko10000.storagepots

import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckolib.misc.ItemUtils
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.model.PotInfo
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.strokkur.commands.annotations.Aliases
import net.strokkur.commands.annotations.Command
import net.strokkur.commands.annotations.Executes
import net.strokkur.commands.annotations.Permission
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.inject

@Command("storagepots")
@Aliases("sp")
@Permission("storagepots.command")
class CommandHandler : MyKoinComponent {

    private val plugin: StoragePots by inject()
    private val potManager: PotManager by inject()

    fun register() {
        plugin.lifecycleManager
            .registerEventHandler(LifecycleEvents.COMMANDS.newHandler(LifecycleEventHandler { event ->
                CommandHandlerBrigadier.register(
                    event.registrar()
                )
            }))
    }

    @Executes("reload")
    @Permission("storagepots.reload")
    fun reload(sender: CommandSender) {
        plugin.reloadConfigs()
        sender.sendMessage(parseMM("<green>Configs reloaded."))
    }

    @Executes("give")
    @Permission("storagepots.give")
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
