
plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    compileOnly(projects.micronautHttpServer)
    compileOnly(projects.micronautJacksonDatabind)
    implementation(libs.logback.classic)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautHttpServer)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)

}
