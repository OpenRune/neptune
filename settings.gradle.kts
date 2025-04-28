rootProject.name = "neptune"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
    }
}

pluginManagement {
    plugins {
        kotlin("jvm") version "1.9.0"
        id("org.jmailen.kotlinter") version "5.0.1"
    }
}

include(
    "clientscript-compiler",
    "runescript-compiler",
    "runescript-parser",
    "runescript-runtime",
)
