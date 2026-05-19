# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2026-05-19

### Changed

- **Publishing target**: switched from GitHub Packages (`maven.pkg.github.com/rarebit-one/ktor-armour`) to **Maven Central** (`mavenCentral()`) as public, unauthenticated artifacts. Coordinates unchanged (`one.rarebit.armour:armour-{core,ktor,reporting,retry}`). Consumers can drop the GitHub Packages repository declaration and the `GPR_TOKEN` credentials from their Gradle setup.
- Build now uses [vanniktech/gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) (`0.30.0`) for KMP+Central publishing (per-target artifacts, javadoc jars, in-memory GPG signing, Central Portal upload).

### Added

- `LICENSE` (Apache 2.0) — required by Maven Central.
- Per-module POM metadata so each artifact (`armour-core`, `armour-ktor`, `armour-reporting`, `armour-retry`) shows a distinct name and description on Central.

[0.5.0]: https://github.com/rarebit-one/ktor-armour/releases/tag/v0.5.0
