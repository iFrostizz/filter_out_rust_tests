import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.intellijPlatformBase)
}

group = "com.github.filteroutrusttests"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation("junit:junit:4.13.2")
    // TODO potentially use https://plugins.jetbrains.com/plugin/8195-toml (it's bundled)
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.21.1")

    intellijPlatform {
        rustRover("2026.1")

        bundledModules("intellij.toml.json")
        bundledPlugins("com.jetbrains.rust", "org.toml.lang")

        testFramework(TestFrameworkType.Platform)

        testBundledModules("intellij.toml.json")
        testBundledPlugins("com.jetbrains.rust", "org.toml.lang")

        pluginVerifier()
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
    instrumentCode = true
}

//val runIdeWithPlugins by intellijPlatformTesting.runIde.registering {
//    plugins {
//        bundledModule("intellij.toml.json")
//        bundledPlugins("com.jetbrains.rust", "org.toml.lang")
//    }
//}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    test {
        useJUnit()
        inputs.dir("src/test/testData")
    }
}
