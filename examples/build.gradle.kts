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

data class ExampleRun(val taskSuffix: String, val mainClass: String, val description: String)

/** Runnable MM samples (Gradle {@code JavaExec} tasks). */
val exampleRuns =
    listOf(
        ExampleRun(
            "Quickstart",
            "exchange.godark.examples.Quickstart",
            "Minimal limit sell far from touch, then cancel"),
        ExampleRun(
            "FullTraderExample",
            "exchange.godark.examples.FullTraderExample",
            "Trader reference: callbacks, place / modify / cancel"),
        ExampleRun(
            "RestClientExample",
            "exchange.godark.examples.RestClientExample",
            "REST auth + account reads + public market-data GETs"))

exampleRuns.forEach { ex ->
    tasks.register<JavaExec>("run${ex.taskSuffix}") {
        group = "examples"
        description = ex.description
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(ex.mainClass)
    }
}

tasks.named("build") {
    dependsOn("compileJava")
}
