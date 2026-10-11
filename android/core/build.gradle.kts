// The same model and logic as the desktop app: compiles the sources of ../../core directly.
plugins {
    `java-library`
}

sourceSets {
    main {
        java.setSrcDirs(listOf("../../core/src/main/java"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
}
