rootProject.name = "qalipsis-api"

include(
    ":qalipsis-dev-platform",
    ":qalipsis-api-dev",
    ":qalipsis-api-common",
    ":qalipsis-api-dsl",
    ":qalipsis-api-processors",
    ":qalipsis-test"
)

project(":qalipsis-dev-platform").projectDir = File(rootDir, "dev-platform")
project(":qalipsis-api-dev").projectDir = File(rootDir, "api-dev")
project(":qalipsis-api-common").projectDir = File(rootDir, "api-common")
project(":qalipsis-api-dsl").projectDir = File(rootDir, "api-dsl")
project(":qalipsis-api-processors").projectDir = File(rootDir, "api-processors")
project(":qalipsis-test").projectDir = File(rootDir, "test")