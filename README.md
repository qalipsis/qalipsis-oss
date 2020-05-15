# evolue

To build the project:

1. Start at the root of the project
1. Create the builder image: `docker build ./docker/builder -t evolue-builder`
1. Start the builder image in your local folder:
    1. For Unix-like systems: `docker run -it --rm -v $(pwd):/scripts -w /scripts evolue-builder bash`
    1. For Windows: `docker run -it --rm -v %CD%:/scripts -w /scripts evolue-builder bash`    
1. Now you are "in" the builder image, execute Gradle with the tasks you need: `./gradlew clean asciidoctor assemble`

    **Windows only**: Ensure that git is **not** converting line endings of gradlew from Unix style (LF) to Windows style (CRLF), otherwise the builder will fail when executing `./gradlew`. Automatic conversion can be disabled via `git config core.autocrlf false`.
1. Avoid to restart your image too often in order to keep the Gradle / Maven cache (or map the folders between your machine and the container)