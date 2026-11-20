package com.foldberry.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/*
 * 音声操作の実行部(§5.5。D27)。どれも端末の標準の受け口に渡すだけで、開けたら true。
 * アラーム・タイマーは SET_ALARM 権限(通常の権限)を宣言している。Samsung の時計は SHOW_ALARMS でもこの権限を要求した(v0.4.0)ため、
 * 受け口が権限を求めても落ちないよう SecurityException も失敗として扱う。
 */

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/** 時計アプリにアラームを入れる(画面は出さない)。日付は指定できず、次に来るその時刻に鳴る。 */
internal fun setAlarm(context: Context, hour: Int, minute: Int): Boolean = start(
    context,
    Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, minute)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
)

/** 時計アプリでタイマーを始める(画面は出さない)。 */
internal fun startTimer(context: Context, seconds: Int): Boolean = start(
    context,
    Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
)

/** 電話アプリを番号入りで開く(発信はそちらで押す。発信の権限は使わない。§5.5)。 */
internal fun openDialerWith(context: Context, number: String): Boolean =
    // fromParts は「#」なども符号化する(文字列で "tel:" を組むと # 以降が落ちる)。
    start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.replace(" ", ""), null)))
