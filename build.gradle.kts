import java.util.Properties

plugins {
    alias(libs.plugins.fabric.loom)
}

// Minecraft version to build for (-Pmc=1.21.10), its yarn and Meteor versions come from versions/<mc>.properties
val mc = providers.gradleProperty("mc").get()
val mcProperties = Properties().apply {
    val file = file("versions/$mc.properties")
    require(file.exists()) { "Unsupported Minecraft version $mc, add versions/$mc.properties" }
    file.reader().use { load(it) }
}
fun mcProperty(name: String): String = mcProperties.getProperty(name) ?: error("$name is missing in versions/$mc.properties")

base {
    archivesName = properties["archives_base_name"] as String
    // e.g. 0.1.0+mc1.21.11, GitHub Actions builds get their own version: 0.1.0+mc1.21.11.build.12
    version = libs.versions.mod.version.get() + "+mc" + mc +
        (providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.let { ".build.$it" } ?: "")
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${mcProperty("minecraft_version")}")
    mappings("net.fabricmc:yarn:${mcProperty("yarn_mappings")}:v2")
    modImplementation(libs.fabric.loader)

    // Meteor
    modImplementation("meteordevelopment:meteor-client:${mcProperty("meteor_version")}")

    // Tests for the cracker (plain Java, no game needed)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get().toInt())
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to mcProperty("minecraft_version"),
            "jdk_version" to libs.versions.jdk.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    test {
        useJUnitPlatform()
        // The full 2^48 search is only run on request: ./gradlew test -Dnsc.fullSearch=true
        systemProperty("nsc.fullSearch", System.getProperty("nsc.fullSearch") ?: "false")
        maxHeapSize = "1G"
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = libs.versions.jdk.get().toInt()
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
