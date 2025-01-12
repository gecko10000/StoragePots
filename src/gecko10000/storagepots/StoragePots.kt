package gecko10000.storagepots

import gecko10000.geckolib.config.YamlFileManager
import gecko10000.storagepots.config.Config
import gecko10000.storagepots.di.MyKoinContext
import org.bukkit.plugin.java.JavaPlugin

class StoragePots : JavaPlugin() {

    private val configFile = YamlFileManager(
        configDirectory = dataFolder,
        initialValue = Config(),
        serializer = Config.serializer(),
    )
    val config: Config
        get() = configFile.value

    override fun onEnable() {
        MyKoinContext.init(this)
    }

    fun reloadConfigs() {
        configFile.reload()
    }

}
