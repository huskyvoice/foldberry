package com.foldberry.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.UserManager
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "FoldBerry"

/**
 * @param removable 長押しメニューの「アンインストール」を押せるか(§6)。端末に組み込まれたアプリ(FLAG_SYSTEM。
 *   後から更新したプリインストールも含む)と、端末管理でアンインストールが禁止されているときは false(2026-11-18 オーナー決定)
 */
data class AppEntry(val ref: AppRef, val label: String, val removable: Boolean = true)

sealed interface CatalogState {
    data object Loading : CatalogState
    /** @param defaults 送話キー・カメラの枠が未割り当てのときに開く既定のアプリ(§8。割り当て一覧・バッジで「既定」として見せる) */
    data class Loaded(val apps: List<AppEntry>, val defaults: DefaultApps = DefaultApps()) : CatalogState {
        /** 一覧の項目(ショートカットなら持ち主のアプリ)。 */
        fun entryOf(ref: AppRef): AppEntry? = apps.firstOrNull { it.ref == ref.app }

        /** 画面に出す名前(ショートカットは「Gmail: 新規作成」)。一覧に無いアプリはショートカットの名前だけ、それも無ければ null。 */
        fun labelOf(ref: AppRef): String? = displayLabel(ref, entryOf(ref)?.label)
    }
    data object Failed : CatalogState
}

/**
 * ランチャーから起動できるアプリの一覧・アイコン・起動(仕様書 §6 / §10)。
 * LauncherApps を使い、全パッケージ取得の権限は使わない(manifest の <queries> で MAIN/LAUNCHER を宣言)。
 * 自分のユーザーのアプリだけを扱う(仕事用プロファイルは v1 の対象外)。
 */
class AppCatalog(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val icons = ConcurrentHashMap<AppRef, ImageBitmap>()

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    // refresh() は init から呼ぶので、ここ(init より上)で初期化しておく。下に置くと init の時点で null のまま落ちる。
    private val refreshGeneration = AtomicInteger(0)

    @Volatile
    private var refreshJob: Job? = null

    // インストール・アンインストール・更新の通知(権限不要)。どれも一覧を読み直すだけにする。
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(setOf(packageName))
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(setOf(packageName))
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(setOf(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            changed(packageNames.toSet())
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            changed(packageNames.toSet())
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed(packages: Set<String>) {
        // 更新でアイコンが変わり得るので、該当パッケージのキャッシュを捨てる。
        icons.keys.removeAll { it.packageName in packages }
        refresh()
    }

    /**
     * 送話キー(ACTION_DIAL)・カメラの枠(STILL_IMAGE_CAMERA)で開く既定のアプリ(§8。v0.6.2)。BerryBody の openDialer /
     * openCamera と同じ intent を引き、一覧の中で同じパッケージの最初のアプリにする。既定が決まっていない(選択画面 =
     * パッケージ "android")・一覧に無いときは null。manifest の <queries> で 2 つの intent を宣言している(無いと他アプリが見えない)。
     * 端末側で既定を変えたときは、次の読み直し(アプリの追加・削除・更新)で反映する。
     */
    private fun defaultApps(apps: List<AppEntry>): DefaultApps {
        fun resolve(intent: Intent): AppRef? {
            val pkg = try {
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                Log.w(TAG, "既定のアプリを引けません: ${intent.action}", e)
                null
            }
            if (pkg == null || pkg == "android") return null
            return apps.firstOrNull { it.ref.packageName == pkg }?.ref
        }
        return DefaultApps(
            dialer = resolve(Intent(Intent.ACTION_DIAL)),
            camera = resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )
    }

    /**
     * 一覧を読み直す。続けて呼ばれたときは前の読込みを取り消し、さらに通し番号で古い結果を捨てる
     * (先に始めた読込みが後に終わって、古い一覧で上書きするのを防ぐ。getActivityList 自体は取り消せないため)。
     */
    fun refresh() {
        val generation = refreshGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            val result = try {
                val blocked = uninstallBlocked()
                val apps = launcherApps.getActivityList(null, user)
                    .filter { it.componentName.packageName != context.packageName }
                    .map {
                        AppEntry(
                            AppRef(it.componentName.packageName, it.componentName.className),
                            it.label.toString(),
                            removable = !blocked && (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0,
                        )
                    }
                CatalogState.Loaded(sortByName(apps) { it.label }, defaultApps(apps))
            } catch (e: Exception) {
                Log.e(TAG, "アプリ一覧を取得できません", e)
                CatalogState.Failed
            }
            ensureActive()
            if (generation == refreshGeneration.get()) _state.value = result
        }
    }

    /**
     * アイコンを読み込む(キャッシュあり)。見つからなければ null。
     * ショートカットはショートカット自身のアイコン(Gmail の新規作成なら鉛筆)。読めない(既定のホームでない等)ときは
     * アプリのアイコンを返し、キャッシュしない(既定のホームにされた後で自身のアイコンに変わるように)。
     */
    suspend fun icon(ref: AppRef, sizePx: Int): ImageBitmap? {
        icons[ref]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                ref.shortcut?.let { id ->
                    shortcutInfo(ref.packageName, id)
                        ?.let { launcherApps.getShortcutIconDrawable(it, context.resources.displayMetrics.densityDpi) }
                        ?.toBitmap(sizePx, sizePx)
                        ?.asImageBitmap()
                        ?.also { icons[ref] = it }
                        ?.let { return@withContext it }
                }
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(ref.packageName, ref.activity))
                launcherApps.resolveActivity(intent, user)
                    ?.getIcon(0)
                    ?.toBitmap(sizePx, sizePx)
                    ?.asImageBitmap()
                    ?.also { if (ref.shortcut == null) icons[ref] = it }
            } catch (e: Exception) {
                Log.w(TAG, "アイコンを読めません: $ref", e)
                null
            }
        }
    }

    /**
     * アプリごとのショートカット(固定=manifest・動的=dynamic。2026-11-20 追加)。キー=アプリ(起動画面)、値=rank 順。
     * 各項目の ref はショートカットを指し(shortcut・shortcutLabel 入り)、label はショートカットの名前だけ。
     * 読めるのは既定のホームアプリだけなので、そうでなければ・失敗したら空。
     */
    suspend fun shortcuts(): Map<AppRef, List<AppEntry>> = withContext(Dispatchers.IO) {
        try {
            if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyMap()
            val apps = (state.value as? CatalogState.Loaded)?.apps.orEmpty()
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
            launcherApps.getShortcuts(query, user).orEmpty()
                .filter { it.isEnabled && !it.shortLabel.isNullOrBlank() }
                .sortedBy { it.rank }
                .mapNotNull { info ->
                    // 起動画面が無い動的ショートカットは、そのパッケージの最初の起動画面に付ける。
                    val owner = apps.firstOrNull { it.ref.packageName == info.`package` && it.ref.activity == info.activity?.className }
                        ?: apps.firstOrNull { it.ref.packageName == info.`package` }
                        ?: return@mapNotNull null
                    val label = info.shortLabel.toString()
                    owner.ref to AppEntry(owner.ref.copy(shortcut = info.id, shortcutLabel = label), label, removable = false)
                }
                .groupBy({ it.first }, { it.second })
        } catch (e: Exception) {
            Log.w(TAG, "ショートカットを読めません", e)
            emptyMap()
        }
    }

    /** ID で 1 つのショートカットを引く(アイコン用)。読めなければ null。 */
    private fun shortcutInfo(packageName: String, id: String): ShortcutInfo? {
        if (!launcherApps.hasShortcutHostPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(id))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return launcherApps.getShortcuts(query, user)?.firstOrNull()
    }

    /**
     * 今の一覧にあるか。長押しメニュー・キー選択待ちの間にアンインストールされたアプリを割り当てないために使う
     * (突き合わせは一覧が変わったときにしか走らないので、後から割り当てた壊れた参照は次の増減まで残る)。
     * 一覧がまだ無い(読込み中・失敗)ときは確かめられないので true。ショートカットは持ち主のアプリで見る。
     */
    fun isListed(ref: AppRef): Boolean =
        (state.value as? CatalogState.Loaded)?.apps?.any { it.ref == ref.app } ?: true

    /** 端末の「アプリ情報」画面を開く(§6 の長押しメニュー。権限不要)。失敗したら false。 */
    fun openDetails(ref: AppRef): Boolean = try {
        launcherApps.startAppDetailsActivity(ComponentName(ref.packageName, ref.activity), user, null, null)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "アプリ情報を開けません: $ref", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "アプリ情報を開けません: $ref", e)
        false
    }

    /** 端末管理(仕事用の端末など)でアプリのアンインストールが禁止されているか。 */
    private fun uninstallBlocked(): Boolean {
        val um = context.getSystemService(UserManager::class.java) ?: return false
        return um.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS) || um.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL)
    }

    /**
     * アンインストールの確認を OS に出してもらう(§6 の長押しメニュー。2026-11-18 追加)。消すかどうかは OS のダイアログで決まり、
     * 消えたら LauncherApps の通知で一覧を読み直し、割り当ても既存の突き合わせで外れる。
     * 権限 REQUEST_DELETE_PACKAGES(通常権限)が要る(無いと OS が黙って無視する)。開けなければ false。
     */
    fun uninstall(ref: AppRef): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", ref.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "アンインストールを開けません: $ref", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "アンインストールを開けません: $ref", e)
        false
    }

    /**
     * アプリを起動する。失敗したら false(直前にアンインストール・無効化された等)。
     * ショートカットはショートカットとして開く。既定のホームでない・アプリ側で消えた/無効にされたときは
     * IllegalStateException・ActivityNotFoundException になるので false。
     */
    fun launch(ref: AppRef, sourceBounds: Rect? = null): Boolean = try {
        val shortcut = ref.shortcut
        if (shortcut != null) {
            launcherApps.startShortcut(ref.packageName, shortcut, sourceBounds, null, user)
        } else {
            launcherApps.startMainActivity(ComponentName(ref.packageName, ref.activity), user, sourceBounds, null)
        }
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "起動できません: $ref", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "起動できません: $ref", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "起動できません: $ref", e)
        false
    }
}
