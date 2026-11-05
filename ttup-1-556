pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // OpenMinis Agent Runtime deps (vendored; see OPENMINIS_UPSTREAM.md):
        // RealTimeCutVADLibraryForAndroid ships via JitPack only.
        maven { url = uri("https://jitpack.io") }
        // rclone.aar — Agent backup-destinations feature (SMB/WebDAV/SFTP/S3/FTP).
        // A local build artifact (deps-openminis/build_rclone_android.sh via
        // gomobile), not a vendored binary — see OPENMINIS_UPSTREAM.md.
        flatDir { dirs("app/libs") }
    }
}
rootProject.name = "TeachTrack"
include(":app")
