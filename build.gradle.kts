plugins {
    java
}

group = "io.rkbkr"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    compile("ch.qos.logback", "logback-classic", "1.2.3")
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}