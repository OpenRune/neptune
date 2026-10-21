plugins {
    application
    kotlin("jvm")
}

dependencies {
    api(project(":runescript-compiler"))
    implementation(libs.netty.buffer)
    implementation(libs.fourkoma)
    implementation(libs.gson)
    implementation(libs.gson) {
        exclude("com.google.errorprone", "error_prone_annotations")
    }
    implementation(libs.logback)
}

application {
    applicationName = "cs2"
    mainClass.set("me.filby.neptune.clientscript.compiler.ClientScriptCompilerApplicationKt")
}
