package gecko10000.storagepots.di

import gecko10000.storagepots.CommandHandler
import gecko10000.storagepots.PotManager
import gecko10000.storagepots.StoragePots
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun pluginModules(plugin: StoragePots) = module {
    single { plugin }
    single(createdAtStart = true) { PotManager() }
    single(createdAtStart = true) { CommandHandler() }
    single {
        Json {
            ignoreUnknownKeys = true
        }
    }
}
