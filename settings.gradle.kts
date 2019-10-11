rootProject.name = "evolue"

include(
        "evolue-api-parent",
        "evolue-api-parent:evolue-api-common",
        "evolue-api-parent:evolue-api-java",
        "evolue-api-parent:evolue-api-kotlin",
        "evolue-api-parent:evolue-api-groovy"
)

findProject(":evolue-api-parent:evolue-api-common")?.name = "evolue-api-common"
findProject(":evolue-api-parent:evolue-api-java")?.name = "evolue-api-java"
findProject(":evolue-api-parent:evolue-api-kotlin")?.name = "evolue-api-kotlin"
findProject(":evolue-api-parent:evolue-api-groovy")?.name = "evolue-api-groovy"

