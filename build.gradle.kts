import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    kotlin("jvm") version "2.3.10"
    id("java-library")
    id("maven-publish")
    kotlin("plugin.serialization") version "2.3.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("de.eldoria.plugin-yml.bukkit") version "0.6.0"
    kotlin("kapt") version "2.3.10"
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
    maven("https://eldonexus.de/repository/maven-public/")
}

dependencies {
    compileOnly(kotlin("stdlib", version = "2.3.10"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("gecko10000.geckolib:GeckoLib:1.1")
    compileOnly("net.strokkur.commands:annotations-paper:2.0.2")
    kapt("net.strokkur.commands:processor-paper:2.0.2")
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
