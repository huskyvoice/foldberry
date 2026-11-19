plugins {
    alias(libs.plugins.android.application)
    // Compose を有効にするには Compose Compiler プラグインが必須(Kotlin 2.0 以降)。
    alias(libs.plugins.kotlin.compose)
    // 割り当ての保存(§9)を JSON にするため。
    alias(libs.plugins.kotlin.serialization)
}

// KEY=VALUE 形式のファイルを設定キャッシュが追跡できる形で読む。
// File.readText() だとキャッシュ後の変更を拾えず、古い versionCode でビルドされ得る。
fun readKeyValues(relativePath: String): Map<String, String>? =
    providers.fileContents(rootProject.layout.projectDirectory.file(relativePath))
        .asText.orNull
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
        ?.associate { (key, value) -> key.trim() to value.trim() }

val versionProps = readKeyValues("version.properties")
    ?: throw GradleException("version.properties が見つかりません。")

// 署名鍵が無いときは新しく作らずに止める(仕様書 §11.4)。鍵が変わると上書きインストールできない。
val signingEnv = readKeyValues(".env")
    ?: throw GradleException(
        ".env がありません。初回なら scripts/fb.sh keygen で署名鍵を作ってください。" +
            "既に鍵がある場合は作り直さず、バックアップから keystore/ と .env を戻してください。"
    )

android {
    namespace = "com.foldberry.launcher"
    compileSdk = 37
    // Dockerfile で固定したものを使わせる。未指定だと AGP 既定の版をコンテナ内へ勝手にダウンロードする。
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.foldberry.launcher"
        // 古い端末対応は不要(§11.4)。Fold8 の出荷 OS は確実に上回る想定。実機で確認後に必要なら引き上げる。
        minSdk = 35
        targetSdk = 37
        versionCode = versionProps.getValue("versionCode").toInt()
        versionName = versionProps.getValue("versionName")
        // 設定のバージョン情報に出すビルド日時(§8)。scripts/fb.sh build が書く。check だけのときなど無ければ空(表示しない)。
        // 引用符・バックスラッシュは入らない書式(YYYY-MM-DD HH:MM)だが、念のため外してから埋める。
        val buildTime = versionProps["buildTime"].orEmpty().filter { it != '"' && it != '\\' }
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.layout.projectDirectory
                .file(signingEnv.getValue("FB_KEYSTORE")).asFile
            storePassword = signingEnv.getValue("FB_STORE_PASSWORD")
            keyAlias = signingEnv.getValue("FB_KEY_ALIAS")
            keyPassword = signingEnv.getValue("FB_KEY_PASSWORD")
        }
    }

    // AGP はユニットテストのタスクを testBuildType(既定は debug)にしか作らない。
    // debug を使うと debug 鍵が /root/.android(adb ペアリング情報のボリューム)に自動生成されるので、release でテストする。
    testBuildType = "release"

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // M1 では縮小を入れない(既定で無効)。空に近いアプリで縮小の不具合を踏むのを避ける。
        }
    }

    buildFeatures {
        compose = true
        // ビルド日時(BUILD_TIME)を設定画面に出すため。
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.serialization.json)

    // 構成の判定・寸法計算は Android API に依存させず、JVM の単体テストで検証する。
    testImplementation(libs.junit)
}
