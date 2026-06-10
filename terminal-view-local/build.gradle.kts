plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.view"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    api("com.github.termux.termux-app:terminal-emulator:v0.118.3")
    implementation("androidx.annotation:annotation:1.7.1")
}
