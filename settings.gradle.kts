rootProject.name = "neptune"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
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
