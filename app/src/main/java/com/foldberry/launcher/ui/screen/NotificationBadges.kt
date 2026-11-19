package com.foldberry.launcher.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.foldberry.launcher.ui.rememberOpenNotificationPanel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.NotificationCounts
import com.foldberry.launcher.data.NotificationKind
import com.foldberry.launcher.ui.icons.FoldBerryIcons

private val BadgeColor = Color(0xE0FFFFFF)

/**
 * 時計の日付の右端に出す種類別の通知(§4.2)。BB Classic の「アイコンの下に件数」を横に並べた形。
 * 0 件の種類は出さない。並びは 不在着信・メール・SMS・その他。
 * 高さは件数が 0 件でも常に「アイコン+件数の行」ぶんを取る(通知が来るたびに下のタイルの高さが跳ねないように)。
 *
 * @param iconSize アイコンの一辺(日付の行の高さに合わせる)
 */
@Composable
fun NotificationBadges(counts: NotificationCounts, iconSize: Dp, modifier: Modifier = Modifier) {
    val countSize = iconSize * 0.62f
    val countFont = with(LocalDensity.current) { countSize.toSp() }
    val openPanel = rememberOpenNotificationPanel()
    // 件数の文字の行の高さ(フォントの上下の余白込み)は文字の大きさの約 1.25 倍。
    // タップで通知パネルを開く(2026-11-18 オーナー指定)。時計の左半分のタップ(ClockBlock)より先にここで受けるので、時計アプリは開かない。
    // 0 件のときはタップを付けない: 幅 0 でも Compose は押せる範囲を最小のタッチ領域(48dp 角)まで広げるため、
    // 日付の右端付近を押すとパネルが開いてしまう(2026-11-18 refuter の指摘)。
    val tap = if (counts.total > 0) {
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = stringResource(R.string.status_open_notifications),
            onClick = openPanel,
        )
    } else {
        Modifier
    }
    Row(
        modifier
            .then(tap)
            .height(iconSize + countSize * 1.25f),
        horizontalArrangement = Arrangement.spacedBy(iconSize * 0.5f),
        verticalAlignment = Alignment.Top,
    ) {
        NotificationKind.entries.forEach { kind ->
            val n = counts.count(kind)
            if (n > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        iconFor(kind),
                        contentDescription = pluralStringResource(descriptionFor(kind), n, n),
                        modifier = Modifier.size(iconSize),
                        colorFilter = ColorFilter.tint(BadgeColor),
                    )
                    BasicText(if (n > 99) "99+" else n.toString(), style = TextStyle(color = BadgeColor, fontSize = countFont))
                }
            }
        }
    }
}

private fun iconFor(kind: NotificationKind): ImageVector = when (kind) {
    NotificationKind.MISSED_CALL -> FoldBerryIcons.MissedCall
    NotificationKind.EMAIL -> FoldBerryIcons.Mail
    NotificationKind.SMS -> FoldBerryIcons.Sms
    NotificationKind.OTHER -> FoldBerryIcons.Bell
}

private fun descriptionFor(kind: NotificationKind): Int = when (kind) {
    NotificationKind.MISSED_CALL -> R.plurals.notif_missed_call
    NotificationKind.EMAIL -> R.plurals.notif_email
    NotificationKind.SMS -> R.plurals.notif_sms
    NotificationKind.OTHER -> R.plurals.notif_other
}
