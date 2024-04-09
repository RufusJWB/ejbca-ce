plugins {
    java
}

dependencies {
    compileOnly(project(":modules:cesecore-common"))
    compileOnly(project(":modules:cesecore-ejb-interface"))
    compileOnly(project(":modules:ejbca-ejb-interface"))
    compileOnly(project(":modules:ejbca-common"))
    compileOnly(project(":modules:ejbca-common-web"))
    compileOnly(libs.bcpkix)
    compileOnly(libs.bcprov)
    compileOnly(libs.bctls)
    compileOnly(libs.bcutil)
    compileOnly(libs.json.simple)
    compileOnly(libs.commons.collections4)
    compileOnly(libs.commons.lang)
    compileOnly(libs.jsch)
    compileOnly(libs.log4j.v12.api)
    compileOnly(libs.x509.common.util)
}

sourceSets {
    val main by getting {
        java {
            setSrcDirs(listOf("src"))
        }
    }
}

tasks.jar {
    from(sourceSets["main"].output)
    from("resources/META-INF/services") {
        into("META-INF/services")
    }
}
