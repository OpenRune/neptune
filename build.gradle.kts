import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

defaultTasks("build")

plugins {
    base
    kotlin("jvm")
    id("org.jmailen.kotlinter") apply false
    id("maven-publish")
}

allprojects {

    group = "me.filby"
    version = "0.0.3-openrune"

    plugins.withType<BasePlugin> {
        configure<BasePluginExtension> {
            archivesName.set("${rootProject.name}-$name")
        }
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            withSourcesJar()

            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(11)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "java")

    plugins.withType<KotlinPluginWrapper> {
        apply(plugin = "org.jmailen.kotlinter")

        dependencies {
            implementation(libs.inlineLogger)
            implementation(libs.guava) {
                exclude("com.google.code.findbugs", "jsr305")
                exclude("com.google.errorprone", "error_prone_annotations")
                exclude("com.google.j2objc", "j2objc-annotations")
                exclude("org.codehaus.mojo", "animal-sniffer-annotations")
            }

            testImplementation(kotlin("test-junit5"))
            testImplementation(libs.junit.api)
            testImplementation(libs.junit.params)

            testRuntimeOnly(libs.junit.engine)
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                artifactId = project.name

                pom {
                    name.set("OpenRune - ${project.name}")
                    description.set("Module ${project.name} of the OpenRune project.")
                    url.set("https://github.com/OpenRune")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://opensource.org/licenses/Apache-2.0")
                        }
                    }

                    developers {
                        developer {
                            id.set("openrune")
                            name.set("OpenRune Team")
                            email.set("contact@openrune.dev")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/OpenRune.git")
                        developerConnection.set("scm:git:ssh://github.com/OpenRune.git")
                        url.set("https://github.com/OpenRune")
                    }
                }
            }
        }

        repositories {
            maven {
                url = uri("E:\\RSPS\\OpenRune\\hosting")
            }
        }
    }

}
