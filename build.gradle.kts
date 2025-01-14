import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "1.4.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("de.eldoria.plugin-yml.bukkit") version "0.6.0"
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("res")
        }
    }
}

group = "gecko10000.storagepots"
version = "0.1"

bukkit {
    name = "StoragePots"
    main = "$group.$name"
    apiVersion = "1.13"
    depend = listOf("GeckoLib")
    permissions {
        register("storagepots.toggleauto") {
            description = "Allows you to toggle auto upgrades"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://redempt.dev/")
}

dependencies {
    compileOnly(kotlin("stdlib", version = "2.0.21"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("gecko10000.geckolib:GeckoLib:1.0-SNAPSHOT")
    compileOnly("com.github.Redempt:RedLib:6.6.1")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}


tasks.register("update") {
    dependsOn(tasks.build)
    doLast {
        exec {
            workingDir(".")
            commandLine("../../dot/local/bin/update.sh")
        }
    }
}
