import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
    id("com.diffplug.spotless") version "7.2.1"
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
    implementation("me.shedaniel.cloth:cloth-config-fabric:${providers.gradleProperty("cloth_config_version").get()}")
    compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.7.1")
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.7.1")
    }
}

val clangFormatSources =
    provider {
        (
            fileTree("src/main/java") {
                include("**/*.java")
            }.files +
                fileTree("src/main/native") {
                    include("**/*.h", "**/*.hpp", "**/*.c", "**/*.cc", "**/*.cpp", "**/*.m", "**/*.mm")
                }.files
        ).map { it.absolutePath }.sorted()
    }

val formatClangSources by tasks.registering(Exec::class) {
    group = "formatting"
    description = "Formats Java and native sources with clang-format."
    inputs.files(clangFormatSources)

    doFirst {
        commandLine("clang-format", "-i", *clangFormatSources.get().toTypedArray())
    }
}

tasks.register("formatCode") {
    group = "formatting"
    description = "Formats Kotlin, Java, and native sources."
    dependsOn("spotlessApply", formatClangSources)
}

tasks.register("printReleaseMetadata") {
    group = "help"
    description = "Prints release metadata consumed by CI."

    doLast {
        val releaseProperties =
            listOf(
                "mod_version",
                "minecraft_version",
                "loader_version",
            )

        releaseProperties.forEach { propertyName ->
            println("$propertyName=${providers.gradleProperty(propertyName).get()}")
        }
    }
}

val isMacosBuildHost = System.getProperty("os.name").lowercase().contains("mac")
val nativeSource = layout.projectDirectory.file("src/main/native/macos/mousee_macos_raw_mouse.mm")
val generatedNativeResources = layout.buildDirectory.dir("generated/mouseeNativeResources")
val macosNativeLibrary = generatedNativeResources.map { it.file("natives/macos/libmousee_macos.dylib") }

val compileMacosNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles the universal macOS GameController raw mouse JNI library."
    onlyIf { isMacosBuildHost }

    inputs.file(nativeSource)
    outputs.file(macosNativeLibrary)

    doFirst {
        macosNativeLibrary
            .get()
            .asFile.parentFile
            .mkdirs()
    }

    val javaHome = providers.systemProperty("java.home")
    commandLine(
        "clang++",
        "-dynamiclib",
        "-fobjc-arc",
        "-std=c++17",
        "-mmacosx-version-min=11.0",
        "-arch",
        "arm64",
        "-arch",
        "x86_64",
        "-I${javaHome.get()}/include",
        "-I${javaHome.get()}/include/darwin",
        "-framework",
        "Foundation",
        "-framework",
        "GameController",
        "-o",
        macosNativeLibrary.get().asFile.absolutePath,
        nativeSource.asFile.absolutePath,
    )
}

tasks.processResources {
    val modVersion = project.version
    inputs.property("version", modVersion)

    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }

    if (isMacosBuildHost) {
        dependsOn(compileMacosNative)
        from(generatedNativeResources)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
