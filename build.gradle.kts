import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.intellijPlatformBase)
}

group = "com.github.filteroutrusttests"
version = "1.0.0"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        rustRover("2026.1")

        bundledPlugins("com.jetbrains.rust", "org.toml.lang", "intellij.json")
        bundledModules("intellij.json.backend", "intellij.toml.json")

        testFramework(TestFrameworkType.Platform)

        pluginVerifier()
    }

    testImplementation("junit:junit:4.13.2")
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

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    test {
        useJUnit()
        inputs.dir("src/test/testData")
        systemProperty("testDataPath", project.projectDir.resolve("src/test/testData").absolutePath)
//        systemProperty("idea.log.debug.categories", "com.intellij")
//        systemProperty("idea.log.trace.categories", "com.intellij")
    }
}
