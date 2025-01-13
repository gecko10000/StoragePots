package gecko10000.storagepots

import gecko10000.geckolib.config.YamlFileManager
import gecko10000.storagepots.config.Config
import gecko10000.storagepots.di.MyKoinComponent
import gecko10000.storagepots.di.MyKoinContext
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.inject

class StoragePots : JavaPlugin(), MyKoinComponent {

    private val configFile = YamlFileManager(
        configDirectory = dataFolder,
        initialValue = Config(),
        serializer = Config.serializer(),
    )
    val config: Config
        get() = configFile.value

    private val potManager: PotManager by inject()
    private val guiManager: GUIManager by inject()

    override fun onEnable() {
        MyKoinContext.init(this)
    }

    override fun onDisable() {
        potManager.saveAll()
        guiManager.shutdown()
    }

    fun reloadConfigs() {
        configFile.reload()
    }

}
