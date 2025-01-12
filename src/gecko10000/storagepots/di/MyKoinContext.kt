package gecko10000.storagepots.di

import gecko10000.storagepots.StoragePots
import org.koin.core.Koin
import org.koin.dsl.koinApplication

object MyKoinContext {
    internal lateinit var koin: Koin
    fun init(plugin: StoragePots) {
        koin = koinApplication(createEagerInstances = false) {
            modules(pluginModules(plugin))
        }.koin
        koin.createEagerInstances()
    }
}
