plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val godarkVersion: String =
    providers.gradleProperty("godarkVersion").get()

dependencies {
    implementation(files("../sdk/lib/godark-${godarkVersion}-all.jar"))
}

tasks.register<JavaExec>("runPlaceOrder") {
    group = "examples"
    description = "Build a sample place-order wire payload (offline)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("exchange.godark.examples.PlaceOrder")
}

tasks.register<JavaExec>("runCancelOrder") {
    group = "examples"
    description = "Build a sample cancel-order wire payload (offline)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("exchange.godark.examples.CancelOrder")
}

tasks.register<JavaExec>("runStreamOrderbook") {
    group = "examples"
    description = "Placeholder for market-data streaming (v0.1)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("exchange.godark.examples.StreamOrderbook")
}

tasks.named("build") {
    dependsOn("compileJava")
}
