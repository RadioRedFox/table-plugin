plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":table-annotations"))
    implementation(libs.ksp.symbol.processing.api)
}

kotlin {
    jvmToolchain(17)
}
