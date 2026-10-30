plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}

// PeakSound ist ein eingebautes Modul von PeakCore: dieses Projekt ergibt kein eigenes Plugin-Jar mehr.
// Das fertige Plugin (PeakCore-<Version>.jar) setzt das Projekt PeakVerse/Bundle aus Core und Modulen zusammen.
dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    // Simple Voice Chat ist optional (softdepend): nur zum Kompilieren, zur Laufzeit liefert es der Server
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.25")
    // PeakCore-API: im fertigen Jar ist sie dabei (Core und Module teilen sich einen Classloader), deshalb hier nur zum Kompilieren
    compileOnly("de.peakverse:core-api")
    // Zum Bauen und Weitergeben: Protokoll und Audio-Kern gehoeren zum Jar (die Audio-Bibliotheken dahinter nicht, siehe audio-core)
    implementation(project(":protocol"))
    implementation(project(":audio-core"))

    testCompileOnly("de.peakverse:core-api")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
