#!/bin/sh
set -eu

bundle=${1:?Frontend bundle path required}
test -f "$bundle"
stamp=$(date +%Y%m%d-%H%M%S)
backup=/opt/qingyu/backups/frontend-$stamp
release=/opt/qingyu/releases/frontend-$stamp
install -d -m 700 "$backup"
install -d "$release"
cp /opt/qingyu/frontend/index.html "$backup/index.html"
tar -xzf "$bundle" -C "$release"
test -s "$release/frontend/dist/index.html"
test -d "$release/frontend/dist/assets"
curl -fsS http://127.0.0.1:18080/api/health >/dev/null
# Keep old hashed assets for clients already playing; publish the entry last.
cp -r "$release/frontend/dist/assets/." /opt/qingyu/frontend/assets/
chmod -R a+rX /opt/qingyu/frontend/assets
install -m 644 "$release/frontend/dist/index.html" /opt/qingyu/frontend/index.html.next
mv /opt/qingyu/frontend/index.html.next /opt/qingyu/frontend/index.html
echo "Backup: $backup"
