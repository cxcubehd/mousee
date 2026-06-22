# Releasing

Mousee publishes release jars from GitHub Actions to Modrinth. Release builds run on macOS so the uploaded jar contains the native backend.

## Required GitHub Secrets

Configure these repository secrets before publishing:

- `MODRINTH_ID`: the Modrinth project id.
- `MODRINTH_TOKEN`: a Modrinth API token with permission to create versions for the project.

## Version Metadata

Release metadata comes from `gradle.properties`:

- `mod_version`
- `minecraft_version`
- `loader_version`

The publish workflow reads those values through:

```bash
./gradlew -q printReleaseMetadata
```

Update `mod_version` before creating a release tag.

## GitHub Release Flow

1. Update `mod_version` in `gradle.properties`.
2. Run local checks:

   ```bash
   ./gradlew formatCode
   ./gradlew build
   ```

3. Commit the release changes.
4. Create and publish a GitHub release.
5. The `Publish to Modrinth` workflow builds the jar and uploads it to Modrinth.

GitHub prereleases are published to Modrinth as `beta` versions. Normal GitHub releases are published as `release` versions.

## Manual Publish Flow

The `Publish to Modrinth` workflow can also be run manually from GitHub Actions. Manual runs require:

- A Modrinth version type: `release`, `beta`, or `alpha`.
- Changelog text.

Manual publishing uses the current `mod_version` from the checked-out branch.

## Uploaded Files

Only the runtime jar is uploaded:

```text
build/libs/mousee-${mod_version}.jar
```

Sources jars and other development artifacts are intentionally excluded from Modrinth publishing.
