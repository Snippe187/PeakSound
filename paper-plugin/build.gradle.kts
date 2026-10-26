plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    implementation(project(":protocol"))
    implementation(project(":audio-core"))
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}

// Das Plugin-Jar enthaelt nur unsere Module. Die Audio-Bibliotheken (LGPL) werden NICHT eingeschmolzen,
// sondern beim Serverstart ueber "libraries" in plugin.yml als eigene Jars von Maven Central geladen.
tasks.jar {
    archiveBaseName = "PeakSound-Paper"
    from(project(":protocol").sourceSets["main"].output)
    from(project(":audio-core").sourceSets["main"].output)
}
