#!/bin/sh
#
# 署名鍵の作成。コンテナ内だけで実行される(scripts/fb.sh keygen から呼ばれる)。
# パスワードの生成・利用・保存をこのスクリプト内で完結させ、ホスト側のシェル履歴やログに出さない。
#
set -eu

KEYSTORE=/work/keystore/foldberry.jks
ENVFILE=/work/.env

# 既存の鍵・パスワードは絶対に上書きしない。鍵が変わると上書きインストールできず、
# アンインストール=設定消失になる(仕様書 §11.4 / §14 Critical)。
for f in "$KEYSTORE" "$ENVFILE"; do
    if [ -e "$f" ]; then
        echo "$f が既にあります。作り直しません。" >&2
        exit 1
    fi
done

umask 077
mkdir -p /work/keystore

# 途中で失敗したら作りかけを消す。片方だけ残ると次回の keygen が「既にある」で止まり手詰まりになる。
# ここに来た時点で両方とも存在しないことを確認済みなので、消すのは自分が作った分だけ。
ok=0
cleanup() {
    if [ "$ok" -ne 1 ]; then
        rm -f "$KEYSTORE" "$ENVFILE"
        echo "作成に失敗したため、作りかけの鍵を削除しました。やり直してください。" >&2
    fi
}
trap cleanup EXIT

pw="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-40)"
if [ "${#pw}" -lt 32 ]; then
    echo "パスワードの生成に失敗しました。" >&2
    exit 1
fi

# PKCS12 はストアと鍵のパスワードを同一にする(異なると keytool が警告する)。
keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -alias foldberry \
    -keyalg RSA -keysize 4096 \
    -validity 36500 \
    -dname "CN=FoldBerry, OU=Personal, O=FoldBerry, C=JP" \
    -storepass "$pw" -keypass "$pw" >/dev/null

# FB_ 接頭辞: docker compose がこの .env を変数展開用に読んでも、compose 側では参照しないので無害。
cat > "$ENVFILE" <<EOF
# scripts/fb.sh keygen が生成。Git に追加せず、内容を表示・ログ出力しないこと(仕様書 §14 Critical)。
FB_KEYSTORE=keystore/foldberry.jks
FB_KEY_ALIAS=foldberry
FB_STORE_PASSWORD=$pw
FB_KEY_PASSWORD=$pw
EOF

ok=1
