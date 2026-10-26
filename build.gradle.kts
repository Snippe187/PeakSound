// Gemeinsame Einstellungen aller Module: Java 25, gleiche Gruppe/Version.
subprojects {
    group = property("maven_group") as String
    version = property("mod_version") as String
}
