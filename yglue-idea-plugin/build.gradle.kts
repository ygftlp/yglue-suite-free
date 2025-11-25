import org.jetbrains.intellij.tasks.InitializeIntelliJPluginTask

plugins {
    // 先应用 Java 插件
    id("java")
    // 再是 IntelliJ 插件
    id("org.jetbrains.intellij") version "1.17.2"
}

repositories {
    mavenCentral()
}

java {
    // 用 toolchain 方式约束 JDK 版本，避免用到你系统上的 Java 25 那些乱七八糟的
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    // 使用 2024.2 平台
    version.set("2024.2")
    // 指定社区版 (IC = IntelliJ IDEA Community)
    type.set("IC")

    // 附加插件依赖
    plugins.set(listOf("java"))
}

dependencies {
    implementation("org.json:json:20240303")
    implementation("org.ow2.asm:asm:9.7")
}

tasks {
    patchPluginXml {
        // 2024.2 的 build 号是 242 开头
        sinceBuild.set("242.0")
        // 兼容 242.* 的所有小版本
        untilBuild.set("242.*")
    }

    // 这个配置其实可以不写，交给上面的 toolchain 管
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    // 先不要禁用 initializeIntelliJPlugin，避免破坏 runIde 的初始化流程
    // 如果你一定要禁用，可以等能正常跑起来之后再说
    named<InitializeIntelliJPluginTask>("initializeIntelliJPlugin") {
        enabled = true
    }

    // 可选：给 runIde 加点 JVM 参数，帮助排错
    named("runIde") {
        // 这里如果想写 jvmArgs，可以这样：
        // (this as org.jetbrains.intellij.tasks.RunIdeTask).jvmArgs("-Didea.is.internal=true")
    }


}