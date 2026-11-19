#!/usr/bin/env bash
#
# FoldBerry の操作入口(仕様書 §11.3)。ビルド・署名・端末操作はすべてコンテナ内で実行する。
# PC 上で ./gradlew / java / adb を直接叩くと、ユーザーフォルダに .gradle や .android が作られてしまう(§11.1)。
#
set -euo pipefail

# Git Bash が引数の /work などを D:/... に書き換えるのを止める(仕様書 §14 Warning)。
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

SERVICE=dev
KEYSTORE_REL=keystore/foldberry.jks
ENV_REL=.env
APK_NAME=FoldBerry.apk

info() { printf '==> %s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

dc() { docker compose "$@"; }

ensure_up() {
    local cid
    cid="$(dc ps -q "$SERVICE" 2>/dev/null || true)"
    if [ -n "$cid" ] && [ "$(docker inspect -f '{{.State.Running}}' "$cid" 2>/dev/null || echo false)" = "true" ]; then
        return 0
    fi
    info "ビルド用コンテナを起動します(初回はイメージの作成に時間がかかります)"
    dc up -d --build "$SERVICE" >/dev/null
}

# コンテナ内でコマンドを実行する。
inside() { ensure_up; dc exec -T "$SERVICE" "$@"; }

read_prop() { # read_prop <file> <key>
    sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*\(.*\)\$/\1/p" "$1" | tail -1 | tr -d '\r'
}

require_keystore() {
    # 鍵が無いときは新しく作らずに止める。鍵が変わると上書きインストールできない(§11.4)。
    [ -f "$KEYSTORE_REL" ] || die "$KEYSTORE_REL がありません。scripts/fb.sh keygen で作成してください(既に作った鍵がある場合はバックアップから戻すこと)。"
    [ -f "$ENV_REL" ]      || die "$ENV_REL がありません。鍵とパスワードは対で必要です(バックアップから戻すこと)。"
}

device_connected() {
    inside adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }'
}

require_device() {
    device_connected || die "端末が接続されていません。ワイヤレスデバッグをオンにし、scripts/fb.sh connect <IP> <接続用ポート> をやり直してください(ポートは毎回変わります)。"
}

cmd_doctor() {
    [ $# -eq 0 ] || die "使い方: scripts/fb.sh doctor"
    info "コンテナ内のツール"
    inside sh -c 'java -version 2>&1 | head -1; gradle --version 2>/dev/null | sed -n "s/^Gradle /gradle /p"; adb --version | head -1; echo "apksigner: $(command -v apksigner)"'

    info "gradlew の改行コード"
    if [ -f gradlew ]; then
        if head -1 gradlew | grep -q $'\r'; then
            warn "gradlew が CRLF です。コンテナ内で実行できません"
        else
            echo "LF (OK)"
        fi
    else
        warn "gradlew がまだありません"
    fi

    info "署名鍵"
    if [ -f "$KEYSTORE_REL" ] && [ -f "$ENV_REL" ]; then echo "あり(中身は表示しません)"; else echo "なし(scripts/fb.sh keygen で作成)"; fi

    info "バージョン"
    echo "versionName=$(read_prop version.properties versionName) versionCode=$(read_prop version.properties versionCode)"

    info "端末の接続状態"
    inside adb devices || true

    # §11.1: PC のプロジェクト外に何も作られていないこと。
    info "PC のプロジェクト外に生成物が無いこと"
    local clean=1 d
    for d in "$HOME/.gradle" "$HOME/.android" "$HOME/.m2" "$HOME/AppData/Local/Android"; do
        if [ -e "$d" ]; then warn "存在します: $d"; clean=0; fi
    done
    [ "$clean" = 0 ] || echo "OK(.gradle / .android / .m2 / AppData\\Local\\Android はいずれも無し)"
}

cmd_keygen() {
    [ $# -eq 0 ] || die "使い方: scripts/fb.sh keygen"
    [ ! -e "$KEYSTORE_REL" ] || die "$KEYSTORE_REL が既にあります。鍵は作り直しません。"
    [ ! -e "$ENV_REL" ]      || die "$ENV_REL が既にあります。先に中身を確認してください。"

    info "署名鍵を作成します(パスワードはコンテナ内で生成し、画面にもログにも出しません)"
    inside sh /work/docker/keygen.sh

    info "作成しました: $KEYSTORE_REL / $ENV_REL"
    printf '\n%s\n%s\n' \
        "Critical: この鍵を失うと上書きインストールできず、アンインストール=設定消失になります。" \
        "keystore/ と .env のバックアップを手動で取ってください(Git には入りません)。"
}

bump_version_code() {
    local cur next tmp
    cur="$(read_prop version.properties versionCode)"
    case "$cur" in
        ''|*[!0-9]*) die "version.properties の versionCode が数値ではありません: '$cur'" ;;
    esac
    next=$((cur + 1))
    mkdir -p tmp   # 一時ファイルもプロジェクト内に置く(§11.1)
    tmp="tmp/version.properties.$$"
    # この関数はコマンド置換の中で呼ばれ、bash はそこで set -e を外す。失敗を明示的に止めないと
    # 空の一時ファイルで version.properties を上書きしてしまう。
    sed "s/^[[:space:]]*versionCode[[:space:]]*=.*/versionCode=$next/" version.properties > "$tmp" \
        || { rm -f "$tmp"; die "version.properties の更新に失敗しました(元のファイルは変更していません)"; }
    grep -q "^versionCode=$next\$" "$tmp" \
        || { rm -f "$tmp"; die "versionCode の書き換えを確認できませんでした(元のファイルは変更していません)"; }
    mv "$tmp" version.properties || die "version.properties の置き換えに失敗しました($tmp を確認してください)"
    printf '%s' "$next"
}

set_build_time() {
    # 設定のバージョン情報に出すビルド日時(§8)。日本時間で書く。TZ データの有無に左右されないよう UTC+9 を自分で足す。
    # Gradle は version.properties を設定キャッシュの入力として読むので、書き換えれば次のビルドに反映される。
    local now tmp
    now="$(date -u -d '+9 hours' '+%Y-%m-%d %H:%M')" || die "ビルド日時を取得できません"
    mkdir -p tmp
    tmp="tmp/version.properties.$$"
    if grep -q '^[[:space:]]*buildTime[[:space:]]*=' version.properties; then
        sed "s/^[[:space:]]*buildTime[[:space:]]*=.*/buildTime=$now/" version.properties > "$tmp"
    else
        { cat version.properties; printf 'buildTime=%s\n' "$now"; } > "$tmp"
    fi || { rm -f "$tmp"; die "version.properties の更新に失敗しました(元のファイルは変更していません)"; }
    grep -q "^buildTime=$now\$" "$tmp" && grep -q '^versionCode=' "$tmp" \
        || { rm -f "$tmp"; die "buildTime の書き換えを確認できませんでした(元のファイルは変更していません)"; }
    mv "$tmp" version.properties || die "version.properties の置き換えに失敗しました($tmp を確認してください)"
}

cmd_build() {
    [ $# -eq 0 ] || die "使い方: scripts/fb.sh build"
    require_keystore

    local code name built
    code="$(bump_version_code)"
    set_build_time
    name="$(read_prop version.properties versionName)"
    info "リリースビルド versionName=$name versionCode=$code"

    # 実機検証はリリース版で行う(§11.4)。debug タスクは使わない:
    # debug 鍵が /root/.android(= adb ペアリング情報のボリューム)に自動生成されるため。
    inside ./gradlew :app:assembleRelease

    built="app/build/outputs/apk/release/app-release.apk"
    [ -f "$built" ] || die "APK が見つかりません: $built"

    mkdir -p dist
    cp "$built" "dist/$APK_NAME"

    info "署名の確認"
    inside sh -c "apksigner verify --print-certs /work/dist/$APK_NAME" | sed -n '1,4p'

    info "出力: dist/$APK_NAME"
}

cmd_check() {
    # 検証ゲート(lint + JVM 単体テスト)。成果物を作らないので versionCode は上げない。
    # release 側で実行するのは debug 鍵を adb ボリュームに作らせないため(app/build.gradle.kts の testBuildType)。
    [ $# -eq 0 ] || die "使い方: scripts/fb.sh check"
    require_keystore
    info "lint と単体テストを実行します"
    inside ./gradlew :app:lintRelease :app:testReleaseUnitTest
    info "OK(レポート: app/build/reports/lint-results-release.html / app/build/reports/tests/testReleaseUnitTest/)"
}

cmd_pair() {
    [ $# -eq 3 ] || die "使い方: scripts/fb.sh pair <端末IP> <ペアリング用ポート> <ペアリングコード>"
    info "ペアリングします $1:$2"
    inside adb pair "$1:$2" "$3"
}

cmd_connect() {
    [ $# -eq 2 ] || die "使い方: scripts/fb.sh connect <端末IP> <接続用ポート>"
    info "接続します $1:$2"
    if ! inside adb connect "$1:$2" | tee /dev/stderr | grep -q "connected to"; then
        warn "接続できませんでした。切り分け情報を出します"
        inside sh -c "ping -c 2 -W 2 '$1' || true" || true
        inside sh -c "nc -z -w 3 '$1' '$2' && echo 'ポート $2 は開いています' || echo 'ポート $2 に到達できません'" || true
        printf '%s\n%s\n%s\n%s\n' \
            "確認する順に:" \
            "  1. ワイヤレスデバッグがオンで、表示中の「IP アドレスとポート」と一致しているか(再起動や Wi-Fi 切替で変わります)" \
            "  2. PC と端末が同じネットワークにいるか(AP アイソレーションが有効だと繋がりません)" \
            "  3. 初回は scripts/fb.sh pair が必要(ペアリング用ポートは接続用ポートとは別)"
        die "接続に失敗しました"
    fi
    inside adb devices
}

cmd_install() {
    [ $# -eq 0 ] || die "使い方: scripts/fb.sh install"
    require_device
    [ -f "dist/$APK_NAME" ] || die "dist/$APK_NAME がありません。先に scripts/fb.sh build を実行してください。"
    info "上書きインストールします"
    inside adb install -r "/work/dist/$APK_NAME"
}

cmd_logs() {
    require_device
    local secs="${1:-0}" n out
    case "$secs" in
        ''|*[!0-9]*) die "使い方: scripts/fb.sh logs [記録する秒数]" ;;
    esac

    mkdir -p logs
    n=1
    while [ -e "$(printf 'logs/foldberry-%03d.log' "$n")" ]; do n=$((n + 1)); done
    out="$(printf 'logs/foldberry-%03d.log' "$n")"

    if [ "$secs" -gt 0 ]; then
        info "バッファを消して ${secs} 秒ぶん記録します。この間に端末で FoldBerry を操作してください"
        inside adb logcat -c || warn "logcat バッファの消去に失敗しました(そのまま続行します)"
        inside sh -c "sleep $secs"
    fi

    # FoldBerry 関連とクラッシュだけを残す(§11.3「FoldBerry のログだけ」)。
    inside sh -c 'adb logcat -d -v threadtime -b main -b crash' \
        | grep -Ei 'foldberry|AndroidRuntime|FATAL EXCEPTION' > "$out" || true

    if [ -s "$out" ]; then
        info "保存しました: $out($(wc -l < "$out" | tr -d ' ') 行)"
        cat "$out"
    else
        rm -f "$out"
        warn "該当するログがありませんでした。端末で FoldBerry を起動してから scripts/fb.sh logs <秒数> を試してください"
    fi
}

usage() {
    printf '%s\n' \
        "FoldBerry の操作スクリプト。すべての処理はコンテナ内で実行されます。" \
        "" \
        "  scripts/fb.sh doctor                       環境と接続状態を確認する" \
        "  scripts/fb.sh keygen                       署名鍵を作る(初回のみ。既にあれば何もしない)" \
        "  scripts/fb.sh check                        lint と単体テストを実行する(検証ゲート)" \
        "  scripts/fb.sh build                        versionCode を上げてリリースビルドし dist/FoldBerry.apk に出す" \
        "  scripts/fb.sh pair <IP> <ポート> <コード>  端末とペアリングする(初回のみ)" \
        "  scripts/fb.sh connect <IP> <ポート>        端末に接続する(ポートは毎回変わる)" \
        "  scripts/fb.sh install                      dist/FoldBerry.apk を上書きインストールする" \
        "  scripts/fb.sh logs [秒数]                  FoldBerry 関連の logcat を logs/ に保存して表示する"
}

case "${1:-}" in
    doctor)  shift; cmd_doctor "$@" ;;
    keygen)  shift; cmd_keygen "$@" ;;
    check)   shift; cmd_check "$@" ;;
    build)   shift; cmd_build "$@" ;;
    pair)    shift; cmd_pair "$@" ;;
    connect) shift; cmd_connect "$@" ;;
    install) shift; cmd_install "$@" ;;
    logs)    shift; cmd_logs "$@" ;;
    ''|-h|--help|help) usage ;;
    *) usage; die "不明なコマンド: ${1}" ;;
esac
