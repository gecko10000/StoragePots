import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    kotlin("jvm") version "2.0.21"
    id("java-library")
    id("maven-publish")
    kotlin("plugin.serialization") version "1.4.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("de.eldoria.plugin-yml.bukkit") version "0.6.0"
    kotlin("kapt") version "2.2.0"
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
    softDepend = listOf("EconomyShopGUI", "EconomyShopGUI-Premium")
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
    //maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://eldonexus.de/repository/maven-public/")
}

dependencies {
    compileOnly(kotlin("stdlib", version = "2.0.21"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("gecko10000.geckolib:GeckoLib:1.1")
    compileOnly("me.gypopo:economyshopgui-api:1.9.0")
    //compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("net.strokkur", "strokk-commands-annotations", "1.2.4-SNAPSHOT")
    kapt("net.strokkur", "strokk-commands-processor", "1.2.4-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn(publishToMavenLocal, shadowJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("local") {
            from(components["java"])
            artifactId = "StoragePots"
        }
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
