plugins {
    `java-library`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
}


dependencies {

    api(libs.awssdk.auth)
    api(libs.awssdk.regions)

    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.awssdk.codeartifact)
    implementation(libs.caffeine)
    implementation(libs.bundles.netty.http)
    implementation(libs.slf4j.api)
}
