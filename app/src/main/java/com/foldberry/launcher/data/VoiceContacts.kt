package com.foldberry.launcher.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log

/**
 * 音声操作の「〇〇に電話」で、連絡先から電話番号を名前で引く(§5.5。READ_CONTACTS)。
 * 比べ方は VoiceCommands.contactScore(敬称・空白を除いて 完全一致 > 前方一致 > 部分一致)。
 * 同じ順位なら携帯 → 既定の番号の順。読みがな(ふりがな)では比べない(話した名前がひらがなで返ると漢字の名前に当たらない)。
 */
object VoiceContacts {
    data class Match(val name: String, val number: String)

    private const val LOG_TAG = "FoldBerry"

    fun find(context: Context, spokenName: String): Match? {
        val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY)
        var best: Match? = null
        var bestRank = -1
        try {
            context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val score = VoiceCommands.contactScore(spokenName, name) ?: continue
                    // 名前の近さを先に、同じなら携帯、既定の番号の順。
                    val rank = score * 4 + (if (c.getInt(2) == Phone.TYPE_MOBILE) 2 else 0) + (if (c.getInt(3) != 0) 1 else 0)
                    if (rank > bestRank) {
                        bestRank = rank
                        best = Match(name, number)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "連絡先を読めません", e)
            return null
        }
        return best
    }
}
