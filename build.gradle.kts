plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "dev.plugin.filteroutrusttests"
version = "1.0.0"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        rustRover("2026.1", configure = {
            useCache = true
        })
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        compatiblePlugin("com.jetbrains.rust")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        changeNotes = """
            Initial release:
            <ul>
              <li>Adds a Usage View filter to exclude usages inside Rust <code>#[test]</code> functions.</li>
              <li>Shows the filter toggle only for Rust usage searches.</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
