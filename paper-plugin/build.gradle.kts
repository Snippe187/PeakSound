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
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}

// Das Plugin-Jar enthaelt das Protokoll-Modul (kein Shadow noetig, keine Fremdbibliotheken).
tasks.jar {
    archiveBaseName = "PeakSound-Paper"
    from(project(":protocol").sourceSets["main"].output)
}
