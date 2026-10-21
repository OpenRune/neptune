rootProject.name = "neptune"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
        maven("https://jitpack.io") {
            content {
                includeModule("cc.ekblad", "4koma")
                includeModule("cc.ekblad.konbini", "konbini")
                includeModule("cc.ekblad.konbini", "konbini-jvm")
            }
        }
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
