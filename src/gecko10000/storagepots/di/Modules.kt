package gecko10000.storagepots.di

import gecko10000.storagepots.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun pluginModules(plugin: StoragePots) = module {
    single { plugin }
    single(createdAtStart = true) { PotManager() }
    single(createdAtStart = true) { CommandHandler() }
    single(createdAtStart = true) { GUIManager() }
    single(createdAtStart = true) { ExternalInvListener() }
    single {
        Json {
            ignoreUnknownKeys = true
        }
    }
}
