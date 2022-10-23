package io.github.itzg.ghrel

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

@SuppressWarnings('unused')
class GithubReleaserPlugin implements Plugin<Project> {


    public static final String TASK_GITHUB_RELEASE_APPLICATION = "githubReleaseApplication"
    public static final String TASK_GITHUB_PUBLISH_APPLICATION = "githubPublishApplication"
    public static final String EXTENSION_GITHUB_RELEASER = "githubReleaser"

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin("application", appliedPlugin -> {
            final GithubReleaserExtension extension = project.extensions
                .create(EXTENSION_GITHUB_RELEASER, GithubReleaserExtension.class);

            final TaskProvider<ReleaseApplicationTask> releaseTask =
                project.tasks.register(TASK_GITHUB_RELEASE_APPLICATION, ReleaseApplicationTask, task -> {
                    task.group = "distribution";
                    task.apply(extension);

                    task.applicationTar.set(
                        project.tasks.named( ApplicationPlugin.TASK_DIST_TAR_NAME, Tar.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)
                    );
                    task.applicationZip.set(
                        project.tasks.named(ApplicationPlugin.TASK_DIST_ZIP_NAME, Zip.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)
                    );
                });

            project.tasks.register(TASK_GITHUB_PUBLISH_APPLICATION, PublishApplicationTask, task -> {
                task.group = "distribution";
                task.apply(extension);
                task.dependsOn(releaseTask);

                task.assets.set(releaseTask.flatMap(ReleaseApplicationTask::getAssets));
                final TaskProvider<Zip> distZip = project.tasks.named("distZip", Zip.class);
                task.zipTopDirectory.set(
                    distZip
                        .flatMap(it -> project.providers.provider(() ->
                            it.archiveBaseName.get() + "-" + it.archiveVersion.get()))
                );
                task.applicationName.set(project.name);
                task.applicationVersion.set(
                    distZip.flatMap(AbstractArchiveTask::getArchiveVersion)
                );
            });
        });
    }
}
