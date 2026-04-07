plugins {
    scala
    id("com.gradleup.shadow")
}

group = "com.newsrx"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.18")
    compileOnly("io.github.gitbucket:gitbucket_2.13:4.46.0")
    compileOnly("org.scalatra:scalatra-javax_2.13:3.1.2")
    compileOnly("io.github.json4s:json4s-jackson_2.13:4.1.0")
    compileOnly("javax.servlet:javax.servlet-api:3.1.0")
    
    testImplementation("org.scalatest:scalatest_2.13:3.2.18")
}

scala {
    scalaVersion = "2.13.18"
}

sourceSets {
    main {
        scala {
            setSrcDirs(listOf("src/main/scala", "src/main/java"))
        }
        java {
            setSrcDirs(emptyList<String>())
        }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}