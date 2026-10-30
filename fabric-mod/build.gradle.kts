plugins {
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT"
}

base { archivesName = "PeakSound" }

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

    // PeakCore-Mod (Handshake und Kanal): wird als eigene Mod installiert (depends in der fabric.mod.json), nicht in dieses Jar eingebaut.
    // Im Dev-Client kommt sie ueber den Composite Build (includeBuild("../PeakCore")) dazu; core-client und core-protocol liefert sie als Jar-in-Jar.
    implementation("de.peakverse:core-mod")
    "clientImplementation"("de.peakverse:core-mod")
    compileOnly("de.peakverse:core-client")
    compileOnly("de.peakverse:core-protocol")
    "clientCompileOnly"("de.peakverse:core-client")
    "clientCompileOnly"("de.peakverse:core-protocol")

    implementation(project(":protocol"))
    implementation(project(":audio-core"))
    "clientImplementation"(project(":protocol"))
    "clientImplementation"(project(":audio-core"))
    include(project(":protocol"))
    include(project(":audio-core"))

    // Audio-Bibliotheken (LGPL) als eigene Jars in der Mod (Jar-in-Jar), nicht eingeschmolzen
    for (lib in listOf(
        "net.jthink:jaudiotagger:3.0.1",
        "com.googlecode.soundlibs:jlayer:1.0.1.4",
        "org.jcraft:jorbis:0.0.17",
        "org.jflac:jflac-codec:1.5.2",
        "net.sourceforge.javaflacencoder:java-flac-encoder:0.3.7",
    )) {
        implementation(lib)
        "clientImplementation"(lib)
        include(lib)
    }
}

dependencies {
    testImplementation("de.peakverse:core-client")
    testImplementation("de.peakverse:core-protocol")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 25 }
