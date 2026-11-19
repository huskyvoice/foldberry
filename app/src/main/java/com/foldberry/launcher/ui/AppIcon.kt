package com.foldberry.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AppRef

/**
 * アプリのアイコンを別スレッドで読み込んで返す(読み込み中・見つからないときは null)。
 * キャッシュは AppCatalog 側にあり、大きさは一定(シートの 36dp でもにじまない程度)にそろえる。
 */
@Composable
fun rememberAppIcon(catalog: AppCatalog, ref: AppRef?): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(initialValue = null, catalog, ref) {
        value = ref?.let { catalog.icon(it, ICON_SIZE_PX) }
    }
    return icon
}

private const val ICON_SIZE_PX = 128

/**
 * 本体部(キーの副刻印)・自由枠に出す割り当て済みアイコンの色フィルタ。彩度 0 のグレースケール。
 * 一覧・シートはアプリを探す場面なのでカラーのまま(オーナー指定 2026-11-17)。
 */
val AssignedIconFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * いま割り当て済みアイコンに掛ける色フィルタ。設定でグレースケールを切ると null(カラー)。
 * キーの描画は BerryBody → KeyRows → KeyCap と深いので、引数で通さず BerryBody から配る(§8)。
 */
val LocalAssignedIconFilter = staticCompositionLocalOf<ColorFilter?> { AssignedIconFilter }
