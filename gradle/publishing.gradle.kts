pluginManager.apply(SigningPlugin::class.java)

configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
}

configure<PublishingExtension> {
    publications {
        create("mavenJava", MavenPublication::class) {
            from(components["java"])
            pom {
                val githubRepo = providers.gradleProperty("githubRepo")
                val githubUrl = githubRepo.map { "https://github.com/$it" }

                name.set(providers.gradleProperty("projectName"))
                description.set(providers.gradleProperty("projectDescription"))
                url.set(providers.gradleProperty("projectUrl"))
                licenses {
                    license {
                        name.set(providers.gradleProperty("projectLicenseName"))
                        url.set(providers.gradleProperty("projectLicenseUrl"))
                    }
                }
                developers {
                    developer {
                        name.set(providers.gradleProperty("developerName"))
                        email.set(providers.gradleProperty("developerEmail"))
                        url.set(providers.gradleProperty("developerUrl"))
                    }
                }
                scm {
                    url.set(githubUrl.map { "$it/tree/master" })
                    connection.set(githubRepo.map { "scm:git:git://github.com/$it.git" })
                    developerConnection.set(githubRepo.map { "scm:git:ssh://github.com:$it.git" })
                }
                issueManagement {
                    url.set(githubUrl.map { "$it/issues" })
                    system.set("GitHub")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri("$buildDir/repos/releases")
        }
    }
}


configure<SigningExtension> {
    val publishing: PublishingExtension by extensions
    sign(publishing.publications["mavenJava"])
}
