# Contributing to QALIPSIS

Thank you for considering a contribution to QALIPSIS. This document explains how to get involved, what to expect from the review process, and the conventions the project follows.

QALIPSIS is an enterprise-grade load, performance, and end-to-end testing tool designed for distributed systems. It is
developed in Kotlin, built with Gradle, and runs on JDK 11+. The open-source edition is dual-licensed: the API and
build-time modules are under **Apache-2.0**, the runtime modules under **AGPL-3.0 or a commercial license**, at the
licensee's choice. The community plugins, the examples and the bootstrap project are under **Apache-2.0**.
See [Licensing](#licensing) before adding a file.

---

## Where to Contribute

The QALIPSIS codebase is spread across several repositories under the [github.com/qalipsis](https://github.com/qalipsis) organization. Each repository covers a distinct part of the project:

| Repository | Purpose |
|---|---|
| `qalipsis-oss` | Core platform — head, factory, runtime, DSL, and scenario engine |
| `qalipsis-gradle-plugin` | Gradle plugin for bootstrapping projects and CI/CD integration |
| `qalipsis-plugin-netty` | HTTP, TCP, UDP, and MQTT steps via Netty |
| `qalipsis-plugin-kafka` | Apache Kafka consume and produce steps |
| `qalipsis-plugin-cassandra` | Apache Cassandra poll, save, and search steps |
| `qalipsis-plugin-elasticsearch` | Elasticsearch steps |
| `qalipsis-plugin-mongodb` | MongoDB steps |
| `qalipsis-plugin-jackson` | JSON, XML, and CSV deserialization via Jackson |
| `qalipsis-plugin-jms` | JMS messaging steps |
| `qalipsis-plugin-jakarta-ee-messaging` | Jakarta EE messaging steps |
| `qalipsis-plugin-r2dbc-jasync` | Reactive SQL steps via R2DBC / jasync |
| `qalipsis-plugin-influxdb` | InfluxDB steps |
| `qalipsis-plugin-graphite` | Graphite steps (pickle and plaintext protocols) |
| `qalipsis-plugin-timescaledb` | TimescaleDB steps |
| `qalipsis-plugin-redis-lettuce` | Redis steps via Lettuce |
| `qalipsis-plugin-rabbitmq` | RabbitMQ steps |
| `qalipsis-plugin-mail` | Email steps |
| `qalipsis-plugin-slack` | Slack notification steps |
| `qalipsis-plugin-sql` | SQL steps |
| `qalipsis-documentation` | User documentation |
| `qalipsis-examples` | Example scenarios demonstrating plugin usage |
| `bootstrap-project` | Starter template for new QALIPSIS projects |

Pick the repository that matches the area you want to improve. If you are unsure, open an issue in `qalipsis-oss` and describe what you have in mind.

---

## Types of Contributions

Contributions are not limited to code. The following are all valuable:

- **Bug reports** — Detailed issue reports with steps to reproduce, expected behavior, and actual behavior: https://github.com/qalipsis/qalipsis-oss/issues.
- **Feature requests** — Descriptions of new capabilities or workflow improvements, ideally with a concrete use case: https://github.com/qalipsis/qalipsis-oss/issues.
- **Plugin development** — New plugins that connect QALIPSIS to additional technologies, protocols, or data stores.
- **Documentation improvements** — Fixes to existing docs, new tutorials, or clarifications of DSL usage and configuration options.
- **Example scenarios** — New examples in the `qalipsis-examples` repository that demonstrate realistic testing patterns.
- **Bug fixes and code improvements** — Patches that resolve reported issues, improve performance, or reduce technical debt.
- **Test coverage** — Additional unit or integration tests that strengthen the project's reliability.

---

## Prerequisites

Before working on a contribution, make sure you have the following set up locally:

- **JDK 11 or later** (the project is built and tested against JDK 11; later versions are compatible)
- **Gradle** (the Gradle Wrapper is included in every repository — use `./gradlew` rather than a system-installed Gradle)
- **Kotlin** (the project is written in Kotlin; familiarity with Kotlin or Java is needed for code contributions)
- **An IDE** — IntelliJ IDEA is recommended. VS Code with the [Kotlin](https://marketplace.visualstudio.com/items?itemName=fwcd.kotlin), [Java](https://marketplace.visualstudio.com/items?itemName=redhat.java), and [Gradle](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) extensions also works well.
- **Docker** (recommended) — Several plugins require running services (Kafka, Cassandra, Redis, etc.) for integration tests. Docker simplifies standing up these dependencies.

---

## Getting Started

### 1. Fork and clone

Fork the relevant repository on GitHub, then clone your fork locally:

```bash
git clone https://github.com/<your-username>/<repository>.git
cd <repository>
```

### 2. Build

```bash
./gradlew clean assemble
```

### 3. Run tests

```bash
./gradlew check
```

Some plugin repositories have integration tests that require external services (databases, message brokers, etc.). Check the repository's README for any Docker Compose files or test setup instructions specific to that plugin.

### 4. Create a branch

Create a branch from `main` with a descriptive name:

```bash
git checkout -b bugfix/describe-the-change
```

Use a prefix that indicates the type of change: `bugfix/`, `feature/`, `docs/`, `techdebt/`.

---

## Contribution Workflow

1. **Open an issue first** (for non-trivial changes). Describe the problem or feature you want to address. This lets maintainers provide early guidance and avoids duplicate work.
2. **Develop your change** on a feature branch in your fork.
3. **Write or update tests** to cover the change. Contributions that change behavior should include corresponding tests.
4. **Make sure the build passes** — run `./gradlew clean assemble check` before pushing.
5. **Submit a pull request** against the `main` branch of the upstream repository. Reference the related issue in the PR description.
6. **Respond to review feedback.** Maintainers may request changes, ask clarifying questions, or suggest alternative approaches. This is a normal part of the process.

---

## Coding Conventions

The project follows a consistent set of conventions to keep the codebase readable and maintainable:

- **Language**: Kotlin. All production code and scenarios are written in Kotlin to take advantage of the JVM ecosystem and Kotlin's concise syntax.
- **Formatting**: Follow the existing code style in the repository. If a `.editorconfig` or formatter configuration is present, use it.
- **Naming**: Classes, functions, and variables should use clear, descriptive names. Step specifications use a fluent DSL style (e.g., `kafka().consume { ... }`, `netty().tcp { ... }`).
- **Plugin structure**: Each plugin is a separate Gradle module that adds its dependencies to the classpath via `implementation` or `runtimeOnly`. Plugins are optional — they should not introduce unnecessary coupling to the core.
- **Documentation**: Public APIs and DSL entry points should include KDoc comments. If your change introduces a new step or configuration option, update the relevant documentation.

---

## Writing a New Plugin

QALIPSIS uses a plugin architecture to keep the core lightweight and reduce coupling between technologies. If you want to add support for a new protocol or data store, creating a plugin is the right approach.

A plugin can contain one or more of the following:

- **Steps** that perform actions — executing requests on a remote system, polling data from a source, or transforming data.
- **Events loggers** for custom event output.
- **Meter registries** for custom metrics export.

When developing a plugin:

1. Create a new repository or Gradle module following the naming pattern `qalipsis-plugin-<technology>`.
2. Declare the plugin dependency using the standard artifact naming convention: `io.qalipsis.plugin:qalipsis-plugin-<technology>`.
3. Implement step specifications using the QALIPSIS DSL — each step should define a clear namespace (e.g., `myPlugin().myStep { ... }`).
4. Include unit tests and, where feasible, integration tests that run against the target technology (typically via Docker containers).
5. Provide at least one example scenario in your repository or as a contribution to `qalipsis-examples`.
6. Document the plugin's steps, parameters, and configuration options.

Refer to the existing plugin repositories (e.g., `qalipsis-plugin-kafka`, `qalipsis-plugin-cassandra`) for structural reference.

---

## Contributing Example Scenarios

The [qalipsis-examples](https://github.com/qalipsis/qalipsis-examples) repository is a good entry point for contributors who want to demonstrate realistic test patterns without modifying the core platform.

When adding a new example:

- Place it in its own directory named after the technology or use case.
- Include a `README.md` that explains what the scenario does, what prerequisites are needed (e.g., a running Kafka cluster), and how to run it.
- Use the QALIPSIS Gradle plugin and `build.gradle.kts` to declare dependencies.
- Keep the scenario focused — demonstrate one concept or plugin clearly rather than combining everything.

---

## Documentation Contributions

The QALIPSIS documentation is written in AsciiDoc. If you want to improve it:

- Fix typos, broken links, or unclear explanations.
- Add missing parameter descriptions for plugin steps.
- Write new tutorials or how-to guides.
- Improve code examples to show more complete or realistic patterns.

Documentation changes follow the same pull request process as code changes.

---

## Reporting Bugs

When filing a bug report, include as much of the following as possible:

- **QALIPSIS version** (and plugin version if applicable).
- **JDK version** and operating system.
- **Steps to reproduce** the problem, ideally with a minimal scenario.
- **Expected behavior** vs. **actual behavior**.
- **Stack traces or log output** if available.
- **Deployment mode** — standalone, cluster, or embedded.

---

## Requesting Features

Feature requests are welcome. When describing a feature:

- Explain the **use case** — what problem does the feature solve, and in what context?
- Describe the **expected behavior** from a user's perspective.
- If possible, suggest an **approach** — how might the feature fit into the existing architecture or DSL?

---

## Licensing

QALIPSIS OSS is distributed under two licenses, depending on the role of a module.

| Scope                                                                                                                                                                                                                                 | License                                                                                                                                      |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Everything under `api/` — `qalipsis-api-dsl`, `qalipsis-api-common`, `qalipsis-api-dev`, `qalipsis-api-processors`, `qalipsis-test`, `qalipsis-dev-platform` — `qalipsis-plugin-platform`, and the Docker base images under `docker/` | **Apache License 2.0** — [`LICENSE-APACHE-2.0`](./LICENSE-APACHE-2.0)                                                                        |
| All the other modules — head, factory, runtime, shared, cluster, webapp — required at runtime only                                                                                                                                    | **Dual: GNU AGPL v3.0 or commercial**, at the licensee's choice — [`LICENSE`](./LICENSE), [`LICENSE-COMMERCIAL.md`](./LICENSE-COMMERCIAL.md) |

The API and the build-time surface are permissive, so that scenario and plugin authors can write, distribute, and keep
proprietary their own test code. The runtime that executes those scenarios is available under the AGPL, or under a
commercial license that replaces the AGPL obligations — the license on which the QALIPSIS commercial and SaaS editions
are built.

When adding a file, copy the license header from a neighbouring file **of the same module**: the two tiers must never be
mixed. The same rule applies to dependencies — an Apache-licensed module must not pull in a copyleft library.
`./gradlew checkLicense` enforces both lists:

- [`api/build-config/allowed-licenses.json`](./api/build-config/allowed-licenses.json) — the stricter list, applied to
  the Apache-licensed modules.
- [`build-config/allowed-licenses.json`](./build-config/allowed-licenses.json) — applied to the AGPL modules.

---

## Contributor License Agreement

Before your first pull request can be merged, you must sign
the [QALIPSIS Individual Contributor License Agreement](./cla/individual-cla.md). The CLA bot posts the signing link as
a comment on the pull request; signing takes a minute and is remembered for all your later contributions. Contributing
on behalf of an employer requires the [Corporate Contributor License Agreement](./cla/corporate-cla.md), signed by
someone entitled to bind the company.

The CLA does **not** transfer your copyright. You keep it, and grant AERIS IT Solutions GmbH a non-exclusive right to
distribute your contribution — under the license of the module you contributed to, under the commercial license of the
dual-licensed modules, and in the commercial and SaaS editions built on the same core. Without that grant, a contributed
file can never move between the two tiers above, nor be relicensed later without tracking every past contributor down.

Your contribution must be your own work, or work you have the right to submit. Do not paste code from another project
unless its license allows it and you say so explicitly in the pull request.

---

## Code of Conduct

Be respectful, constructive, and collaborative. Treat other contributors and maintainers the way you would want to be treated in a professional engineering environment. Harassment, personal attacks, and deliberately unconstructive behavior are not acceptable.

---

## Getting Help

- **Documentation**: [docs.qalipsis.io](https://docs.qalipsis.io)
- **Bootstrap a project**: [bootstrap.qalipsis.io](https://bootstrap.qalipsis.io) or clone the [bootstrap-project](https://github.com/qalipsis/bootstrap-project) repository.
- **Example scenarios**: [github.com/qalipsis/qalipsis-examples](https://github.com/qalipsis/qalipsis-examples)
- **Issues**: [Open an issue in `qalipsis-oss`](https://github.com/qalipsis/qalipsis-oss/issues) for general questions.

We appreciate your time and effort. Every contribution — whether a one-line doc fix or a new plugin — makes QALIPSIS better for the community.