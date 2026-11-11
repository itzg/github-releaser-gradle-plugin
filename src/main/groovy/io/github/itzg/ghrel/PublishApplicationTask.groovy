package io.github.itzg.ghrel

import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import io.github.itzg.ghrel.Assets.Asset
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

/**
 * Publishes the application to package manager repositories, such as a Scoop Bucket or Brew Tap.
 */
abstract class PublishApplicationTask extends DefaultTask {

    private static final JsonGenerator RUBY_STRING_GENERATOR =
        new JsonGenerator.Options().disableUnicodeEscaping().build()

    @Internal
    abstract Property<Assets> getAssets();

    @Internal
    abstract Property<String> getApplicationName();

    @Internal
    abstract Property<String> getApplicationVersion();

    @Internal
    abstract Property<String> getZipTopDirectory();

    @Internal
    abstract RegularFileProperty getApplicationTar();

    @Internal
    abstract Property<String> getTarVersion();

    private GithubReleaserExtension extension;

    void apply(GithubReleaserExtension extension) {

        this.extension = extension;
    }

    @TaskAction
    void publish() {
        final GitHub gitHub;
        try {
            gitHub = new GitHubBuilder()
                .withEndpoint(extension.githubApiUrl.get())
                .withOAuthToken(
                    extension.publish.githubPublishToken
                        .orElse(extension.githubToken)
                        .get()
                )
                .build();
        } catch (IOException e) {
            throw new RuntimeException("Allocating GitHub client for publishing", e);
        }

        if (extension.publish.scoop.repository.present) {
            publishToScoopBucket(gitHub);
        }

        if (extension.publish.homebrew.repository.present) {
            publishToBrewTap(gitHub)
        }
    }

    private void publishToScoopBucket(GitHub gitHub) {
        final PublishProperties publishProps = extension.publish;
        final ScoopProperties scoopProps = publishProps.scoop;

        final String name = applicationName.get();
        final String bucketDirectory = scoopProps.bucketDirectory.orNull;
        final String contentPath = bucketDirectory != null ? "${bucketDirectory}/${name}.json" : "${name}.json"
        final Asset zipFile = assets.get().zipFile;
        final String version = applicationVersion.get();

        def manifest = [
            'version'    : version,
            'url'        : zipFile.downloadUrl,
            'hash'       : "sha256:${zipFile.sha256}",
            'bin'        : "bin/${name}.bat",
            'extract_dir': zipTopDirectory.get(),
            'suggest'    : [
                'JRE': ['java/temurin-lts-jre']
            ]
        ]
        extension.project.withConfiguredDescription(project, { description ->
            manifest.description = description
        })
        def contentBuilder = new JsonBuilder(manifest)

        pushFileToRepo(
            gitHub, scoopProps.repository, scoopProps.branch, 'Scoop bucket',
            contentPath, contentBuilder.toPrettyString()
        )
    }

    private void publishToBrewTap(GitHub gitHub) {
        def homebrew = extension.publish.homebrew
        def name = applicationName.get()
        def binaryPath = findTarBinaryPath(project, applicationTar.get().asFile, name)
        def cask = renderCask(name, tarVersion.get(), assets.get().tarFile, binaryPath,
            extension.project.resolveDescription(project), extension.project.homepage.getOrElse(''),
            homebrew.javaDependency.get())
        pushFileToRepo(gitHub, homebrew.repository, homebrew.branch, 'Homebrew tap',
            "Casks/${name}.rb", cask)
    }

    static String findTarBinaryPath(org.gradle.api.Project project, File archive, String name) {
        def matches = [] as Set<String>
        project.tarTree(archive).visit { details ->
            def path = details.relativePath.pathString
            if (!details.directory && (path == "bin/${name}" || path.endsWith("/bin/${name}"))) {
                matches.add(path)
            }
        }
        if (matches.size() != 1) {
            throw new GradleException("Expected one bin/${name} executable in ${archive}, found ${matches.size()}: ${matches}")
        }
        return matches.first()
    }

    static String renderCask(String name, String version, Asset tarFile, String binaryPath,
                             String description, String homepage, String javaDependency) {
        if (!version || version.trim().isEmpty() || version == 'latest' || !(version ==~ /[0-9a-zA-Z.,_+ -]+/)) {
            throw new GradleException("Invalid Homebrew cask version '${version}': use a non-empty version containing only letters, digits, dots, commas, underscores, plus signs, hyphens, or spaces (not 'latest')")
        }
        return """cask ${rubyString(name)} do
  version ${rubyString(version)}
  sha256 ${rubyString(tarFile.sha256)}

  url ${rubyString(tarFile.downloadUrl)}
  name ${rubyString(name)}
  desc ${rubyString(description)}
  homepage ${rubyString(homepage)}

  depends_on formula: ${rubyString(javaDependency)}

  binary ${rubyString(binaryPath)}
end
"""
    }

    private static String rubyString(String value) {
        return RUBY_STRING_GENERATOR.toJson(value).replace('#{', '\\#{')
    }

    private void pushFileToRepo(GitHub gitHub, Property<String> repositoryName, Property<String> branch,
                                String publicationType,
                                String contentPath, String content) {
        final GHRepository repo;
        try {
            repo = gitHub.getRepository(repositoryName.get());
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed accessing ${publicationType} repository ${repositoryName.get()}: ${e.message}", e);
        }

        final String targetBranch = branch.orElse(project.provider(repo::getDefaultBranch)).get()
        GHContent existingContent;
        try {
            existingContent = repo.getFileContent(contentPath, targetBranch);
        } catch (FileNotFoundException ignored) {
            existingContent = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            logger.info("Publishing to {} {}", publicationType, repo.fullName);
            repo.createContent()
                .branch(targetBranch)
                .sha(existingContent != null ? existingContent.sha : null)
                .content(content)
                .path(contentPath)
                .message("${applicationName.get()}: released ${applicationVersion.get()}")
                .commit();
        } catch (IOException e) {
            throw new RuntimeException("Publishing manifest update", e);
        }
    }
}
