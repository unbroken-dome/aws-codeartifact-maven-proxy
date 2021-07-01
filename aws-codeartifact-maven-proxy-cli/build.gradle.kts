plugins {
    application
    kotlin("jvm")
    id("com.google.cloud.tools.jib") version "3.1.1"
}


dependencies {
    implementation(project(":aws-codeartifact-maven-proxy"))
    implementation(libs.joptsimple)
    implementation(libs.bundles.log4j)
}


application {
    applicationName = "aws-codeartifact-maven-proxy"
    mainClass.set("org.unbrokendome.awscodeartifact.mavenproxy.cli.CodeArtifactMavenProxyCli")
}


tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
}


tasks.named<Tar>("distTar") {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}


jib {
    from {
        image = "adoptopenjdk:11.0.11_9-jre-openj9-0.26.0-focal"
    }
    to {
        image = "unbroken-dome/aws-codeartifact-maven-proxy"
        tags = setOf(project.version.toString())
    }
}
