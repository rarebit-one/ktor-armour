---
name: publish-maven
description: "Publish a Kotlin Multiplatform library to Maven Central via CI. Use when the user says 'publish maven', 'release ktor-armour', 'cut a release', 'release maven', or '/publish-maven'. Handles version bump, changelog, build verification, PR, tagging, and CI-driven publish."
---

# Publish Maven Central Skill

Prepare and publish ktor-armour to Maven Central via GitHub Actions.

Publishing is done by CI (not locally) — pushing a `v*` tag triggers the `release.yml` workflow which delegates to `rarebit-one/.github/.github/workflows/reusable-maven-central-release.yml@v1`. The reusable creates a GitHub Release from the CHANGELOG entry, then builds + signs + publishes all KMP target artifacts to Maven Central via vanniktech/gradle-maven-publish-plugin (`publishAndReleaseToMavenCentral`).

This skill handles everything up to and including the tag push.

## Usage

```
/publish-maven              # Full release flow
/publish-maven --dry-run    # Verify everything, stop before PR creation
```

## Workflow

### 1. Verify Context

Confirm we're in ktor-armour (or a similarly-shaped KMP+Central library):

```bash
git branch --show-current

# Must have gradle.properties with VERSION_NAME and GROUP keys
grep -E "^(VERSION_NAME|GROUP)=" gradle.properties

# Must have the release workflow delegating to the reusable
test -f .github/workflows/release.yml && \
  grep -q "reusable-maven-central-release.yml" .github/workflows/release.yml
```

**Blockers:**

- `gradle.properties` missing `VERSION_NAME` or `GROUP` — stop, not set up for vanniktech publishing
- `.github/workflows/release.yml` missing or not delegating to the reusable — wire it up first (see `rarebit-one/.github` repo for the v1 reference)

### 2. Create the Release Worktree (mandatory)

This repo's `enforce-worktree.sh` PreToolUse hook blocks file edits in the main checkout. Open a worktree first; all subsequent edits + the bump-PR branch live there:

```bash
# Resolve the target version first so the worktree path matches
VERSION=$(grep '^VERSION_NAME=' gradle.properties | cut -d'=' -f2-)
# If Step 3 detects this version is already published, the user picks a
# new version here and we'll use that one for the rest of the run.

git fetch origin main
git worktree add .worktrees/release-v$VERSION -b chore/release-v$VERSION origin/main
cd .worktrees/release-v$VERSION
```

The bump branch is created at worktree-add time, so Step 9 won't need a separate `git checkout -b`.

### 3. Extract Project Metadata

```bash
GROUP=$(grep '^GROUP=' gradle.properties | cut -d'=' -f2-)
VERSION=$(grep '^VERSION_NAME=' gradle.properties | cut -d'=' -f2-)

# Modules = the included Gradle subprojects (one Maven artifact per module).
# settings.gradle.kts is the authoritative source — don't infer from directory
# listings (those include gradle/, build/, .worktrees/, etc.).
MODULES=$(grep '^include(' settings.gradle.kts | sed 's/.*include.*[":]\([^"]*\)".*/\1/' | sed 's@^:@@')
echo "$MODULES"
```

Also read and display:

- Current `VERSION_NAME` from `gradle.properties`
- CHANGELOG.md entry for this version (if exists)
- The set of modules that will publish (typically `armour-core`, `armour-ktor`, `armour-reporting`, `armour-retry`)

### 4. Pre-Publish Checks

```bash
# Check if this version is already published on Maven Central (anonymous probe)
# Probe one module — they all bump together so a single check is enough.
PRIMARY_MODULE=armour-core
GROUP_PATH=$(echo "$GROUP" | tr '.' '/')
curl -sS -o /dev/null -w "%{http_code}" \
  "https://repo1.maven.org/maven2/${GROUP_PATH}/${PRIMARY_MODULE}/${VERSION}/${PRIMARY_MODULE}-${VERSION}.pom"
# 404 = unpublished (good), 200 = already on Central (need a bump)

# CHANGELOG state — three possible states:
HAS_VERSION_ENTRY=$(grep -c "^## \[${VERSION}\]" CHANGELOG.md || echo 0)
UNRELEASED_BODY=$(awk '/^## \[Unreleased\]/{flag=1;next} /^## \[/{flag=0} flag' CHANGELOG.md | grep -v '^\s*$')
```

**Blockers:**

- Version already published on Central — ask the user what version to bump to (suggest next patch/minor/major). If the user declines, abort.
- `## [<VERSION>]` entry does **not** exist AND `[Unreleased]` section is empty — there are no notes for the release. Ask the user to add notes before continuing (Step 6 would rotate empty content otherwise).

Note: it is normal at this point for `## [<VERSION>]` to be absent — Step 6 creates it by rotating `[Unreleased]` content into a versioned entry. Only block when there are no notes to rotate.

### 5. Version Bump (if needed)

When the current version is already published, or the user requests a bump:

1. Update `VERSION_NAME=<new>` in `gradle.properties`

Vanniktech reads `VERSION_NAME` from `gradle.properties`; no per-module file edits are needed (each module's `build.gradle.kts` no longer declares `version = "..."` after the Maven Central migration — see `feat: publish to Maven Central` PR for context).

### 6. Update CHANGELOG.md

Rotate `[Unreleased]` → versioned entry:

1. Rename `## [Unreleased]` to `## [<version>] - <today's date>` (format: `YYYY-MM-DD`).
2. Add a new empty `## [Unreleased]` heading above it.
3. Update the link references at the bottom of `CHANGELOG.md`:
   ```markdown
   [Unreleased]: https://github.com/rarebit-one/ktor-armour/compare/v<version>...HEAD
   [<version>]: https://github.com/rarebit-one/ktor-armour/releases/tag/v<version>
   ```
   - The `[Unreleased]` diff link should point at `v<version>...HEAD` so future commits compare cleanly.
   - The `[<version>]` link should point at the release tag (created later in Step 11).

If Step 4 reported `[Unreleased]` was empty AND no `## [<version>]` exists, you should have aborted already. If you are here, there are notes to rotate.

### 7. Run Tests

```bash
./gradlew test --no-daemon --no-configuration-cache
```

This runs JVM tests for all modules. Native iOS targets are compiled but not tested locally (they're built in CI on macos-latest where simulators are available).

Running `test` separately before `build` (Step 8) is intentional: faster fail-fast feedback on test failures before the longer KMP build kicks off. `build` would also run tests, but the lag is uncomfortable in interactive use.

**Blockers:**

- Tests fail — stop and ask the user to fix.

### 8. Build + Publish Verification

Validate that the Gradle publish pipeline works end-to-end **without** actually uploading to Central. Two passes:

```bash
# Pass 1: full build of all KMP targets + verify javadoc/sources jars + verify POM
./gradlew build --no-daemon --no-configuration-cache

# Pass 2: dry-run publish to local Maven (~/.m2/repository) with signing disabled.
# This confirms vanniktech wires up correctly without needing the GPG key in scope.
./gradlew publishToMavenLocal --no-daemon --no-configuration-cache -PRELEASE_SIGNING_ENABLED=false

# Confirm the artifacts landed in mavenLocal
GROUP_PATH=$(grep '^GROUP=' gradle.properties | cut -d'=' -f2- | tr '.' '/')
VERSION=$(grep '^VERSION_NAME=' gradle.properties | cut -d'=' -f2-)
ls ~/.m2/repository/${GROUP_PATH}/armour-core/${VERSION}/ 2>&1 | head -10
```

Expect to see `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module`, and `kotlin-tooling-metadata.json` per module.

**Blockers:**

- `./gradlew build` fails — fix and retry.
- `publishToMavenLocal` fails — pom/metadata generation is broken; fix before tagging.

### 9. Release Summary

Present a summary before proceeding:

```
## Release Summary

Group:       <GROUP>
Version:     <VERSION_NAME>
Modules:     armour-core, armour-ktor, armour-reporting, armour-retry
Targets:     jvm, iosX64, iosArm64, iosSimulatorArm64
Registry:    https://repo1.maven.org (published by CI via reusable workflow)
Reusable:    rarebit-one/.github/.../reusable-maven-central-release.yml@v1

Changelog:
  <first ~10 lines of the version's CHANGELOG entry>

Next steps:
  1. Open the version-bump PR (this skill creates it)
  2. Merge PR (manual, your call)
  3. Tag v<version> → CI publishes to Maven Central + creates GitHub Release
  4. Central propagation: ~5-15 min typical, but first publishes under a
     namespace can take longer (Sonatype manually reviews first-time deploys).
     Subsequent publishes auto-promote quickly.
```

If `--dry-run` was passed, stop here.

**Dry-run cleanup:** Steps 5–6 already modified `gradle.properties` and `CHANGELOG.md` in the worktree. They are uncommitted. To revert:

```bash
git restore gradle.properties CHANGELOG.md
```

Alternatively, leave them in the worktree for inspection — the worktree is isolated from main and can be removed entirely with `git worktree remove .worktrees/release-v<version> --force` once you're done reviewing.

### 10. Create Version Bump PR

The bump branch (`chore/release-v<version>`) was created at worktree time (Step 2), so we only need to add + commit + push:

```bash
# Still inside .worktrees/release-v<version>/
git add gradle.properties CHANGELOG.md
git commit -m "chore: Release v<version>"
git push -u origin chore/release-v<version>

gh pr create --title "chore: Release v<version>" --body "$(cat <<'EOF'
## Release v<version>

<changelog entry for this version>

After merging, let me know and I'll tag `v<version>`, which triggers CI to:
- Create a GitHub Release with the CHANGELOG notes for this version
- Build all KMP targets on macos-latest
- Sign artifacts with the env-scoped GPG key
- Publish to Maven Central via `publishAndReleaseToMavenCentral`
  (uploads to Sonatype Central Portal, then auto-releases to the public
  Central repository)

The full publish flow runs in the `maven-central` GitHub Environment, gated
by the deployment-branch policy (tags matching `v*` only).
EOF
)"
```

Tell the user to review and merge the PR, then notify you when it's merged so you can proceed to Step 11.

### 11. Tag the Release (after PR merge)

When the user confirms the PR is merged, leave the worktree and tag from the main checkout:

```bash
cd <main-checkout-root>   # i.e. cd ../..  if currently in .worktrees/release-vX.Y.Z

# Pull the merged commit. ff-only fails loudly if anything else is dirty —
# safer than `reset --hard`, which would silently discard local divergent state.
git checkout main
git fetch origin main
git pull --ff-only origin main

# Tag the merged commit (annotated tag — required for the reusable workflow)
git tag -a v<version> -m "Release v<version>"
git push origin v<version>

# Worktree + bump branch cleanup
git worktree remove .worktrees/release-v<version>
git branch -D chore/release-v<version> 2>/dev/null || true
# Remote branch is auto-deleted on merge by GitHub's repo setting; if not, clean up:
git push origin --delete chore/release-v<version> 2>/dev/null || true
```

The tag push triggers `.github/workflows/release.yml` → reusable workflow which:

1. Verifies the tag version matches `gradle.properties:VERSION_NAME`
2. Extracts the CHANGELOG entry for this version and creates a GitHub Release
3. Builds all KMP targets on macos-latest
4. Publishes signed artifacts to Maven Central and auto-releases the staging repository

### 12. Output

Report:

1. Maven Central URL: `https://central.sonatype.com/artifact/<GROUP>/armour-core/<VERSION>` (available after publish completes)
2. Search URL: `https://search.maven.org/artifact/<GROUP>/armour-core/<VERSION>/jar` (appears once Central indexing settles)
3. Git tag created: `v<version>`
4. GitHub Release + Central publish: triggered by CI — link: `https://github.com/rarebit-one/ktor-armour/actions`
5. Remind: indexing on `repo1.maven.org` can take 5-15 min after the publish workflow completes. The Central Portal URL is queryable sooner; the `repo1.maven.org` mirror is what consumers' `mavenCentral()` actually hits.

## Consumer follow-ups (out of scope)

This skill does **not** bump consumers. There are three known consumers:

- `rarebit-one/luminality-app` — `gradle/libs.versions.toml` key `ktorArmour`
- `sidekick-labs/sidekick-companion-kit` — `gradle/libs.versions.toml` key `ktor-armour`
- `sidekick-labs/sidekick-admin-kit` — direct `api("one.rarebit.armour:armour-core:<version>")` in `shared/build.gradle.kts`

Dependabot's `gradle` ecosystem on each consumer will open bump PRs automatically once Maven Central indexes the new version. There is no `rollout-maven` skill — see workspace `CLAUDE.md` for the workspace-isolation rationale.

## Error Handling

| Error | Solution |
|-------|----------|
| `./gradlew build` fails | Fix and retry |
| `publishToMavenLocal` fails | Inspect generated POMs in `~/.m2/repository/<group>/<module>/<version>/` |
| Tests fail | Fix before proceeding |
| Tag already exists | Version was previously tagged — skip tagging (but check that the previous release succeeded) |
| Tag push fails | Likely a permissions issue — report and continue |
| Reusable workflow tag verification fails | `VERSION_NAME` in `gradle.properties` doesn't match the tag — fix one to align |
| CHANGELOG entry missing | The reusable's awk extractor fails on missing `## [version]` heading — add the entry before tagging |
| `repo1.maven.org` returns 404 after publish | Normal for the first 5-15 min. First-namespace publishes can take longer (Sonatype manual review). Check the Central Portal UI at `https://central.sonatype.com/publishing` for deployment status |
| CI publish fails | Check the Actions tab — common causes: `maven-central` environment secrets missing (`SIGNING_KEY`, `SIGNING_KEY_ID`, `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`), or vanniktech plugin version mismatch with Gradle. Note: there is **no** `SIGNING_KEY_PASSWORD` secret — the GPG key was generated passphrase-free, and the workflow passes an empty `signingInMemoryKeyPassword` accordingly |
