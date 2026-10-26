plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    // Simple Voice Chat ist optional (softdepend): nur zum Kompilieren, zur Laufzeit liefert es der Server
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.25")
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
