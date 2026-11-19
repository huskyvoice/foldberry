package com.foldberry.launcher.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.core.content.edit

/**
 * 画面の言語(日本語/英語。既定は日本語。§8 / 2026-11-17 追加)。
 * Android 13 以降のアプリ別の言語設定を使うので、選んだ言語は OS が覚える(assignments.json・書出しファイルには入れない。§9)。
 * 設定すると OS が Activity を作り直して反映する(manifest の configChanges に locale を入れていないため)。
 */
internal object AppLanguage {
    const val JAPANESE = "ja"
    const val ENGLISH = "en"
    private const val DEFAULT = JAPANESE

    /**
     * いまの言語(ja / en)。アプリの言語が未設定(OS で「システムのデフォルト」を選んだ)なら、
     * 実際に画面に出ている言語(リソースの構成)を返す。
     */
    fun current(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        val language = if (locales.isEmpty) context.resources.configuration.locales[0].language else locales[0].language
        return if (language == ENGLISH) ENGLISH else JAPANESE
    }

    fun set(context: Context, tag: String) {
        if (current(context) == tag && !unset(context)) return
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }

    /**
     * 初めて起動したときだけ日本語にする(端末が英語でも既定は日本語。オーナー指定)。
     * その後に OS の「アプリの言語」で「システムのデフォルト」を選ぶと言語は未設定に戻るが、それは上書きしない
     * (アプリ側の判断より OS での選択を優先。2026-11-17 オーナー決定)。初回かどうかは SharedPreferences で覚える。
     * v0.5.1 の途中版で既に言語が設定済みなら、初回扱いにせず印だけ付ける。
     * @return 設定した(=このあと Activity が作り直される)なら true
     */
    fun ensureDefault(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return false
        prefs.edit { putBoolean(KEY_INITIALIZED, true) }
        if (!unset(context)) return false
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(DEFAULT)
        return true
    }

    private const val PREFS = "foldberry"
    private const val KEY_INITIALIZED = "language_initialized"

    private fun unset(context: Context) = context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty
}
