plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories { mavenCentral() }

// Die LGPL-Bibliotheken werden nie in unser Jar eingeschmolzen: Das Plugin laedt sie per
// plugin.yml "libraries", die Mod bindet sie als eigene Jars (Jar-in-Jar) ein.
dependencies {
    api(project(":protocol"))
    api("net.jthink:jaudiotagger:3.0.1")                         // Tags, Cover, Laenge (LGPL)
    api("com.googlecode.soundlibs:jlayer:1.0.1.4")               // MP3-Decoder (LGPL)
    api("org.jflac:jflac-codec:1.5.2")                           // FLAC-Decoder (LGPL)
    api("net.sourceforge.javaflacencoder:java-flac-encoder:0.3.7") // FLAC-Encoder (LGPL)

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
