plugins {
  java
  id("com.github.node-gradle.node") version "7.0.2"
  id("com.palantir.docker") version "0.36.0"
}

description = "QALIPSIS Web OSS"
version = File(rootDir, "project.version").readText().trim()

node {
  version = "20.11.0"
  yarnVersion = "1.22.19"
  download = true
}

val dockerTag = "${project.version}".lowercase()
docker {
    name = "zakd79ka.gra7.container-registry.ovh.net/oss/${project.name}:${dockerTag}"

    if (System.getenv("GITHUB_ACTIONS") != "true") {
        platform("linux/amd64", "linux/arm64")
    }
    buildx(true)
    files(".output", "docker")
    noCache(true)
}

tasks {
    create("cleanNode", Delete::class.java) {
      group = "build"
      delete("node_modules", "package-lock.json", ".output", "dist")
    }

    create("cleanCache", Delete::class.java) {
      group = "build"
      delete(".nuxt")
    }

    named("clean") {
        dependsOn("cleanNode", "cleanCache")
    }

    create("buildDocker", com.github.gradle.node.yarn.task.YarnTask::class.java) {
      group = "build"
      args.set(listOf("generate-docker"))
      dependsOn("clean", "npmInstall")
    }

    create("buildProd", com.github.gradle.node.yarn.task.YarnTask::class.java) {
      group = "build"
      args.set(listOf("generate-prod"))
      dependsOn("clean", "npmInstall")
    }

    named("build") {
        dependsOn("buildProd")
    }
}
