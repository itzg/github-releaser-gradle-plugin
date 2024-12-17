## To test

In an adjacent project that is using this plugin, add the following to `settings.gradle`:

```
pluginManagement { 
    includeBuild '../github-releaser-gradle-plugin' 
}
```

Set at least the following environment variables:

- GITHUB_REF_NAME
- GITHUB_REPOSITORY
- GITHUB_TOKEN

To test publishing task, set some or all of:

- HOMEBREW_TAP_REPO
- SCOOP_BUCKET_REPO
