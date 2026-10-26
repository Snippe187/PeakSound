plugins {
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()
    runs {
        // Dev-Client verbindet automatisch mit dem lokalen Testserver (run/server)
        named("client") {
            programArgs("--quickPlayMultiplayer", "localhost:25565", "--username", "PeakDev")
        }
    }
    mods {
        register("peaksound") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

repositories { mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation(project(":protocol"))
    include(project(":protocol"))
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 25 }
