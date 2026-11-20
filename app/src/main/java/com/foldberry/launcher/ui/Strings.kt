package com.foldberry.launcher.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.foldberry.launcher.R
import com.foldberry.launcher.data.BackgroundPreset
import com.foldberry.launcher.data.DateStyle
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.data.HubPreview
import com.foldberry.launcher.data.IconStyle
import com.foldberry.launcher.data.MainPanelKind
import com.foldberry.launcher.data.NameGroup
import com.foldberry.launcher.data.Pictogram
import com.foldberry.launcher.keyboard.AssignmentSlot
import com.foldberry.launcher.keyboard.FunctionKey
import com.foldberry.launcher.keyboard.KeySpec

/*
 * 純粋層(Android 非依存・単体テスト対象)が持つ日本語の呼び名を、画面の言語に合わせて文字列リソースから引き直す。
 * 純粋層の API(KeyLayout.displayName・BackgroundPreset.label)はテストと保存形式のために変えない(多言語対応 2026-11-17)。
 */

/** キーの呼び名(例「W キー」/「W key」。§7)。文言は KeyLayout.displayName と対応させる。 */
internal fun Resources.keyName(spec: KeySpec): String = when (spec.id) {
    "BACKSPACE" -> getString(R.string.key_backspace)
    "ALT" -> getString(R.string.key_alt)
    "DOLLAR" -> getString(R.string.key_dollar)
    "ENTER" -> getString(R.string.key_enter)
    "LEFT_SHIFT" -> getString(R.string.key_left_shift)
    "RIGHT_SHIFT" -> getString(R.string.key_right_shift)
    "ZERO" -> getString(R.string.key_zero)
    "SPACE" -> getString(R.string.key_space)
    "SYM" -> getString(R.string.key_sym)
    else -> getString(R.string.key_name, spec.id)
}

/** 機能列のキーの呼び名(例「送話キー」)。 */
internal fun Resources.functionKeyName(key: FunctionKey): String = getString(
    when (key) {
        FunctionKey.SEND -> R.string.key_fn_send
        FunctionKey.MENU -> R.string.key_fn_menu
        FunctionKey.TRACKPAD -> R.string.key_fn_trackpad
        FunctionKey.BACK -> R.string.key_fn_back
        FunctionKey.END -> R.string.key_fn_end
    },
)

/** 割り当て先の枠の呼び名(設定の割り当て一覧。§8)。例「W キー」「送話キー」「カメラの枠」「ホームの枠 3」「タイル 1」。 */
internal fun Resources.slotName(slot: AssignmentSlot): String = when (slot) {
    is AssignmentSlot.Key -> keyName(slot.spec)
    is AssignmentSlot.Function -> functionKeyName(slot.key)
    is AssignmentSlot.DockCamera -> getString(R.string.dock_camera_name)
    is AssignmentSlot.MainSlot -> getString(if (slot.inFolder) R.string.folder_edit_title else R.string.slot_main, slot.index + 1)
}

/**
 * 割り当て一覧のキートップに略記と並べて添える短い呼び名(§8。v0.6.2)。文字キーは刻印そのもので分かるので null。
 * 記号・修飾キー・機能列・カメラの枠は呼び名、自由枠・タイルは番号が略記(M3・T1)にあるので種類だけ。
 */
internal fun Resources.slotHint(slot: AssignmentSlot): String? = when (slot) {
    is AssignmentSlot.Key -> if (slot.spec.id.length == 1 && slot.spec.id[0] in 'A'..'Z') null else keyName(slot.spec)
    is AssignmentSlot.Function -> functionKeyName(slot.key)
    is AssignmentSlot.DockCamera -> getString(R.string.dock_camera_name)
    is AssignmentSlot.MainSlot -> getString(if (slot.inFolder) R.string.folder_default_name else R.string.slot_hint_main)
}

/** 名前の群の見出し(A–Z・#・あ。割り当て一覧の小見出し・アプリ一覧のページの帯)。 */
@StringRes
internal fun NameGroup.groupLabelRes(): Int = when (this) {
    NameGroup.LATIN -> R.string.name_group_latin
    NameGroup.OTHER -> R.string.name_group_other
    NameGroup.JAPANESE -> R.string.name_group_japanese
}

/** 12/24 時間表記の選択肢の名前(§8)。 */
@StringRes
internal fun HourFormat.labelRes(): Int = when (this) {
    HourFormat.SYSTEM -> R.string.hour_format_system
    HourFormat.H12 -> R.string.hour_format_12
    HourFormat.H24 -> R.string.hour_format_24
}

/** 日付・曜日の書式の名前(設定シートの選択肢。§4.2)。 */
@StringRes
internal fun DateStyle.labelRes(): Int = when (this) {
    DateStyle.STANDARD -> R.string.date_style_standard
    DateStyle.SHORT -> R.string.date_style_short
    DateStyle.WITH_YEAR -> R.string.date_style_year
    DateStyle.NUMERIC -> R.string.date_style_numeric
}

/** メインの枠の部品の名前(設定シートの選択肢。§4.5)。 */
@StringRes
internal fun MainPanelKind.labelRes(): Int = when (this) {
    MainPanelKind.CALENDAR -> R.string.main_panel_calendar
    MainPanelKind.HUB -> R.string.main_panel_hub
}

/** Hub の本文の出し方の名前(設定シートの選択肢。§4.6)。 */
@StringRes
internal fun HubPreview.labelRes(): Int = when (this) {
    HubPreview.FULL -> R.string.hub_preview_full
    HubPreview.SENDER -> R.string.hub_preview_sender
    HubPreview.APP -> R.string.hub_preview_app
}

/** 割り当てアイコンの出し方の名前(設定シートの選択肢。§5.2)。 */
@StringRes
internal fun IconStyle.labelRes(): Int = when (this) {
    IconStyle.STANDARD -> R.string.icon_style_color
    IconStyle.GRAY -> R.string.icon_style_gray
    IconStyle.PICTOGRAM -> R.string.icon_style_pictogram
}

/** 絵記号の名前(割り当てシートの絵記号の行・選ぶページ。§5.2)。 */
@StringRes
internal fun Pictogram.labelRes(): Int = when (this) {
    Pictogram.MAIL -> R.string.pictogram_mail
    Pictogram.CHAT -> R.string.pictogram_chat
    Pictogram.BROWSER -> R.string.pictogram_browser
    Pictogram.MAP -> R.string.pictogram_map
    Pictogram.PHOTO -> R.string.pictogram_photo
    Pictogram.CAMERA -> R.string.pictogram_camera
    Pictogram.PHONE -> R.string.pictogram_phone
    Pictogram.CLOCK -> R.string.pictogram_clock
    Pictogram.CALENDAR -> R.string.pictogram_calendar
    Pictogram.CALCULATOR -> R.string.pictogram_calculator
    Pictogram.NOTE -> R.string.pictogram_note
    Pictogram.VIDEO -> R.string.pictogram_video
    Pictogram.MUSIC -> R.string.pictogram_music
    Pictogram.FILES -> R.string.pictogram_files
    Pictogram.SETTINGS -> R.string.pictogram_settings
    Pictogram.WALLET -> R.string.pictogram_wallet
    Pictogram.SHOPPING -> R.string.pictogram_shopping
    Pictogram.GENERIC -> R.string.pictogram_generic
}

/** 背景プリセットの表示名。 */
@StringRes
internal fun BackgroundPreset.labelRes(): Int = when (this) {
    BackgroundPreset.AURORA -> R.string.preset_aurora
    BackgroundPreset.BERRY -> R.string.preset_berry
    BackgroundPreset.GRAPHITE -> R.string.preset_graphite
    BackgroundPreset.ABYSS -> R.string.preset_abyss
}
