[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.itzg.github-releaser)](https://plugins.gradle.org/plugin/io.github.itzg.github-releaser)

This Gradle plugin uploads the [application plugin's](https://docs.gradle.org/current/userguide/application_plugin.html) distributions to the requested GitHub release and optionally publishes application manifests to a [Homebrew](https://brew.sh/) tap and/or [Scoop](https://scoop.sh/) bucket.

## Tasks

### `githubReleaseApplication`

Releases the artifacts from the [application plugin's](https://docs.gradle.org/current/userguide/application_plugin.html) `distTar` and `distZip` tasks to the assets section of a GitHub release named for the current project version. The release is created if it doesn't already exist.

The repository and GitHub token must be specifically provided, see [configuration](#configuration).

### `githubPublishApplication`

Publishes an application manifest to some or all of the following:

- [Scoop](https://scoop.sh/) Bucket
- [Homebrew](https://brew.sh/) Tap

For each of the respective type(s), the publishing token and repository must be provided.

## Configuration

### `githubReleaser` extension

> A lot of the fields use defaults that align with environment variables provided by GitHub Actions build context

```groovy
githubReleaser {
    /**
     * Defaults:
     * Property: releaseName
     * Env var: GITHUB_REF_NAME
     * Project version
     */
    releaseName

    /**
     * Defaults:
     * Env var: GITHUB_API_URL
     * "https://api.github.com"
     */
    githubApiUrl

    /**
     * Property: repository
     * Env var: GITHUB_REPOSITORY
     */
    githubRepository

    /**
     * Defaults:
     * Env var: GITHUB_TOKEN
     */
    githubToken

    /**
     * Used for published application manifests
     */
    project {
        license
        homepage
    }
    publish {
        /**
         * GitHub access token with push access to repositories configured below.
         * Default:
         * Property: githubPublishToken
         * Env var: GITHUB_PUBLISH_TOKEN
         * Env var: GITHUB_TOKEN
         */
        publishToken
        
        scoop {
            /**
             * Scoop bucket repository as {owner}/{name}
             * Defaults:
             * Property: scoopBucketRepo
             * Env var: SCOOP_BUCKET_REPO
             */
            repository

            /**
             * Defaults:
             * "bucket"
             */
            bucketDirectory
        }
        homebrew {
            /**
             * Homebrew tap repository as {owner}/{name}
             * Defaults:
             * Property: homebrewTapRepo
             * Env var: HOMEBREW_TAP_REPO
             */
            repository

            /**
             * Defaults:
             * Repository's default branch
             */
            branch

            /**
             * Defaults:
             * "java"
             */
            javaDependency
        }
    }
}
```

## Examples

### As a step in a GitHub workflow

```yaml
    - name: Gradle test and publish
      uses: gradle/gradle-build-action@v2
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        GITHUB_PUBLISH_TOKEN: ${{ secrets.PUSH_ACCESS_GITHUB_TOKEN }}
        SCOOP_BUCKET_REPO: itzg/scoop-bucket
        HOMEBREW_TAP_REPO: itzg/homebrew-tap
      with:
        arguments: |
          test 
          githubPublishApplication

```