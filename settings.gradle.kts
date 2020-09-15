rootProject.name = "evolue"

include(
        "api",
        "api:api-common",
        "api:api-dsl",
        "api:api-processors",
        "api:api-processors-test",
        "test",

        "core",
        "runtime",

        "developer-documentation",

        "samples",
        "samples:simple",

        "plugins",
        "plugins:netty",
        "plugins:jackson"
)

