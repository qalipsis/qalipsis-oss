# QALIPSIS

![CI](https://github.com/aeris-consulting/qalipsis-engine/actions/workflows/gradle-master.yml/badge.svg)

## Run an instance of QALIPSIS locally

Read the instructions in [deployment/index.html](./deployment/index.html).

## Build the project in a prepared Docker image

1. Start at the root of the project
1. Create the builder image: `docker build ./docker/builder -t aerisconsulting/openjdk:11`
1. Start the builder image in your local folder:
    1. For Unix-like systems: `docker run -it --rm -v $(pwd):/scripts -w /scripts aerisconsulting/openjdk:11 bash`
    1. For Windows: `docker run -it --rm -v %CD%:/scripts -w /scripts aerisconsulting/openjdk:11 bash`
1. Now you are "in" the builder image, execute Gradle with the tasks you need: `./gradlew clean asciidoctor assemble`

   **Windows only**: Ensure that git is **not** converting line endings of gradlew from Unix style (LF) to Windows
   style (CRLF), otherwise the builder will fail when executing `./gradlew`. Automatic conversion can be disabled
   via `git config core.autocrlf false`.
1. Avoid to restart your image too often in order to keep the Gradle / Maven cache. You can also map the folders between
   your machine and the container and add limits to the CPU and memory to verify that the tests pass under constrained
   resources:
    1. For Unix-like
       systems: `docker run -it --rm --cpus=2 --memory=4g -v $(pwd):/scripts -v $HOME/.m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -w /scripts aerisconsulting/openjdk:11 bash`
    1. For
       Windows: `docker run -it --rm --cpus=2 --memory=4g -v %CD%:/scripts -v %USERPROFILE% /.m2:/root/.m2 -w /scripts aerisconsulting/openjdk:11 bash`  
