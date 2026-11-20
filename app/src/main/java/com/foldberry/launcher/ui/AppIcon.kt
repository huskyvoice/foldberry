package com.foldberry.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.AppearanceSetting
import com.foldberry.launcher.data.IconStyle
import com.foldberry.launcher.data.Pictogram
import com.foldberry.launcher.data.Pictograms
import com.foldberry.launcher.ui.icons.FoldBerryIcons

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
val LocalAssignedIconFilter = staticCompositionLocalOf<ColorFilter?> { null }

/** いまの見た目の設定(割り当てアイコンの出し方と、オーナーが選んだ絵記号)。配り方は LocalAssignedIconFilter と同じ。 */
val LocalAssignedIconLook = staticCompositionLocalOf { AppearanceSetting() }

/** 割り当てアイコンの出し方を配る(BerryBody・MainHomePanel・PortraitTop)。白黒のときだけ色フィルタを掛ける(絵記号は白い線なので不要)。 */
fun assignedIconLocals(appearance: AppearanceSetting): Array<ProvidedValue<*>> = arrayOf(
    LocalAssignedIconFilter provides if (appearance.style == IconStyle.GRAY) AssignedIconFilter else null,
    LocalAssignedIconLook provides appearance,
)

/**
 * 割り当て済みの枠(キーの副刻印・自由枠・フォルダ・カメラの枠)に出すアイコン。Hub の行は対象外(2026-11-20 オーナー決定)(§5.2)。
 * 絵記号の設定ならアプリのアイコンの代わりに絵記号を ImageBitmap に描いて返す(描く側は今までどおり Image で出せる)。
 */
@Composable
fun rememberAssignedIcon(catalog: AppCatalog, ref: AppRef?): ImageBitmap? {
    val look = LocalAssignedIconLook.current
    if (look.style != IconStyle.PICTOGRAM || ref == null) return rememberAppIcon(catalog, ref)
    val chosen = look.pictograms[ref.packageName]?.let { Pictogram.of(it) }
    val auto = rememberAutoPictogram(catalog, ref.packageName)
    val pictogram = chosen ?: auto ?: return null
    return rememberPictogramBitmap(pictogram)
}

/** オーナーが選んでいないときに自動で決まる絵記号(読込み中は null)。選ぶページの「自動」にも出す。 */
@Composable
fun rememberAutoPictogram(catalog: AppCatalog, packageName: String): Pictogram? {
    val auto by produceState<Pictogram?>(initialValue = null, catalog, packageName) {
        value = Pictograms.auto(packageName, catalog.appCategory(packageName))
    }
    return auto
}

/**
 * 絵記号を ICON_SIZE_PX 角の ImageBitmap に描く。アプリのアイコンは枠いっぱいの絵なので、
 * 土台なしの線画は見本(docs/mock/appicon-mock.html の A: 58dp の枠に 40dp)と同じく約 7 割の大きさで中央に置く。
 */
@Composable
private fun rememberPictogramBitmap(pictogram: Pictogram): ImageBitmap {
    val painter = rememberVectorPainter(FoldBerryIcons.Pictograms.of(pictogram))
    val density = LocalDensity.current
    return remember(painter, density) {
        val bitmap = ImageBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
        val size = Size(ICON_SIZE_PX.toFloat(), ICON_SIZE_PX.toFloat())
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
            inset(ICON_SIZE_PX * PICTOGRAM_INSET) {
                with(painter) { draw(this@inset.size) }
            }
        }
        bitmap
    }
}

/** 絵記号の周りの余白(片側)。40/58 ≒ 0.69 → 片側 0.155。 */
private const val PICTOGRAM_INSET = 0.155f
