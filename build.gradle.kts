// AGP 9 以降、Kotlin サポートは AGP に組み込みで org.jetbrains.kotlin.android は適用しない。
// ただし組み込みの KGP は古い版に固定されているため、カタログの kotlin を使わせるには
// KGP を buildscript の classpath に足す(AGP 9.0 リリースノート "Upgrade to a higher KGP version")。
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
