import org.jetbrains.intellij.tasks.InitializeIntelliJPluginTask

plugins {
    id("org.jetbrains.intellij") version "1.17.2"
    java
}

intellij {
    version.set("2024.2")
    plugins.set(listOf("java"))
}

repositories {
    mavenCentral()
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("")
    }
    compileJava {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
}

tasks.named<InitializeIntelliJPluginTask>("initializeIntelliJPlugin") {
    enabled = false
}

dependencies {
    implementation("org.json:json:20240303")
}
