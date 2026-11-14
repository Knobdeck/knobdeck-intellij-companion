import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.harshalkudale.knobdeck"
version = providers.gradleProperty("pluginVersion").get()

java {
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    // Compile against the oldest supported IDE (2024.3) so the plugin runs on every newer one,
    // including Android Studio, which trails IntelliJ IDEA by about a year.
    intellijIdeaCommunity("2024.3.7")
    pluginVerifier()
    zipSigner()
    testFramework(TestFrameworkType.Platform)
  }
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
  pluginConfiguration {
    version = project.version.toString()
    ideaVersion {
      sinceBuild = "243"
      untilBuild = provider { null } // no upper bound; the verifier checks new IDEs in CI
    }
    changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.html")).asText
  }

  pluginVerification {
    ides {
      create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.7")
      // 2025.3 and later ship only as the unified IntelliJ IDEA (no Community build).
      create(IntelliJPlatformType.IntellijIdea, "2025.3.6")
      create(IntelliJPlatformType.IntellijIdea, "2026.3")
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
  }

  buildSearchableOptions = false
}

// /health reports the version from this file, filled in at build time (no IDE API needed).
tasks.processResources {
  val v = project.version.toString()
  inputs.property("version", v)
  filesMatching("knobdeck-companion.properties") { expand("version" to v) }
}

tasks.test {
  // The compat test drives the real KnobDeck `intellij` plugin when these are set.
  for (name in listOf("KNOBDECK_INTELLIJ_PLUGIN", "KNOBDECK_INTELLIJ_SETUP")) {
    val value = providers.environmentVariable(name).getOrElse("")
    environment(name, value)
    inputs.property(name, value)
    if (value.isNotEmpty() && file(value).exists()) inputs.file(value).withPropertyName("$name-binary")
  }
  environment("KNOBDECK_COMPAT_DIR", layout.projectDirectory.dir("../compat").asFile.absolutePath)
}
