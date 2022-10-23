package io.github.itzg.ghrel

import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class ProjectProperties {
    abstract Property<String> getLicense()

    abstract Property<String> getHomepage()

    abstract Property<String> getDescription()

    /**
     * @return the result of the given action or an empty string if description wasn't set
     */
    void withConfiguredDescription(Project project, Closure<String> action) {
        def value = resolveDescription(project)

        if (value != null && !value.isBlank()) {
            action(value)
        }
    }

    String resolveDescription(Project project) {
        description.orElse(project.description ?: '')
            .get()
    }
}
