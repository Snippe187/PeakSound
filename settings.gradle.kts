pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "peaksound"
include("protocol", "audio-core", "paper-plugin", "fabric-mod")

// PeakCore liegt als eigenes Projekt daneben; die API (core-api) wird beim Entwickeln direkt daraus bezogen (Composite Build)
includeBuild("../PeakCore")
