buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("com.github.jruby-gradle.base:com.github.jruby-gradle.base.gradle.plugin:2.+")
    }
}

// To later generate Github pages: https://github.com/asciidoctor/asciidoctor-gradle-examples/tree/master/asciidoc-to-github-pages-example

plugins {
    java
    "com.github.jruby-gradle.base"
    id("org.asciidoctor.jvm.convert") version "3.1.0"
    id("org.asciidoctor.jvm.pdf") version "3.1.0"
    id("org.asciidoctor.jvm.gems") version "3.1.0"
}

description = "Technical documentation for developers and architects"

repositories {
    mavenCentral()
}
dependencies {
    asciidoctorGems("rubygems:asciidoctor-diagram:2.0.1")
    asciidoctorGems("rubygems:rouge:3.16.0")
}

asciidoctorj {
    modules {
        diagram.use()
        diagram.version("1.5.16")

        pdf.use()
    }
}

// https://asciidoctor.github.io/asciidoctor-gradle-plugin/development-3.x/user-guide/#_task_configuration
tasks {
    "asciidoctor"(org.asciidoctor.gradle.jvm.AsciidoctorTask::class) {
        setSourceDir(file("src/main/asciidoc"))
        sources(delegateClosureOf<PatternSet> {
            include("**/*.adoc")
        })
        resources(delegateClosureOf<CopySpec> {
            from("src/main/resources") {
            }
            into("./")
        })

        outputOptions {
            backends("html5", "docbook")
            separateOutputDirs = true
        }

        // To later apply the CSS: https://asciidoctor.org/docs/produce-custom-themes-using-asciidoctor-stylesheet-factory/
        options(mapOf("doctype" to "book", "ruby" to "erubis"))
        attributes(mapOf(
            "build-gradle" to file("build.gradle"),
            "outputDir" to file("build/docs"),
            "source-highlighter" to "coderay",
            "snippets" to file("${project.buildDir}/snippets"),
            "toc" to "left",
            "toc-title" to "Table of Contents",
            "toclevels" to "4",
            "icons" to "font",
            "sectlinks" to "",
            "docinfo" to "shared",
            "idseparator" to "-"
        ))

        //dependsOn(*(dependsOn.plus("asciidoctorPdf").toTypedArray()))
    }

    "asciidoctorPdf"(org.asciidoctor.gradle.jvm.pdf.AsciidoctorPdfTask::class) {
        setSourceDir(file("src/main/asciidoc"))
        sources(delegateClosureOf<PatternSet> {
            include("**/*.adoc")
        })

        resources(delegateClosureOf<CopySpec> {
            from("src/resources") {
            }
            into("./")
        })

        // To later apply the CSS: https://asciidoctor.org/docs/produce-custom-themes-using-asciidoctor-stylesheet-factory/
        options(mapOf("doctype" to "book", "ruby" to "erubis"))
        attributes(mapOf(
            "build-gradle" to file("build.gradle"),
            "outputDir" to file("build/docs"),
            "source-highlighter" to "coderay",
            "snippets" to file("${project.buildDir}/snippets"),
            "imagesdir" to "./images",
            "toc" to "top",
            "toc-title" to "Table of Contents",
            "toclevels" to "4",
            "icons" to "font",
            "sectlinks" to "",
            "docinfo" to "shared",
            "idseparator" to "-"
        ))
    }
}
