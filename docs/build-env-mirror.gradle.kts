// Environment-only workaround (NOT part of the project): repo.maven.apache.org / repo1.maven.org return
// HTTP 429 for this sandbox's shared egress IP, and plugins.gradle.org redirects to them.
// Google's GCS mirror of Maven Central serves the same artifacts.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun org.gradle.api.artifacts.repositories.ArtifactRepository.redirectCentral() {
    if (this is org.gradle.api.artifacts.repositories.MavenArtifactRepository &&
        url.toString().startsWith("https://repo.maven.apache.org/maven2")) {
        setUrl(mirror)
    }
}
beforeSettings {
    // Added first, so it is consulted before the plugin portal; 404s fall through to the next repository.
    pluginManagement.repositories.maven { setUrl(mirror) }
    pluginManagement.repositories.configureEach { redirectCentral() }
    dependencyResolutionManagement.repositories.configureEach { redirectCentral() }
}
