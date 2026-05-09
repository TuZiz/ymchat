plugins {
    java
}

group = "ym"
version = "1.0.0-SNAPSHOT"

val shaded by configurations.creating

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.lucko.me/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.5")
    testImplementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    shaded("com.zaxxer:HikariCP:6.3.0") {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    shaded("org.postgresql:postgresql:42.7.5") {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    shaded("net.kyori:adventure-text-serializer-legacy:4.17.0") {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    shaded("net.kyori:adventure-text-serializer-plain:4.17.0") {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    shaded("net.kyori:adventure-text-serializer-gson:4.17.0") {
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(
        shaded.map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}
