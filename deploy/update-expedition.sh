#!/bin/sh
set -eu

bundle=${1:-/home/ubuntu/qingyu-expedition.tar.gz}
test -f "$bundle"
stamp=$(date +%Y%m%d-%H%M%S)
backup=/opt/qingyu/backups/expedition-$stamp
release=/opt/qingyu/releases/expedition-$stamp

install -d -m 700 "$backup"
sudo -u postgres pg_dump worldgame -Fc > "$backup/world.dump"
cp /opt/qingyu/game.jar "$backup/game.jar"
cp /opt/qingyu/frontend/index.html "$backup/index.html"
install -d "$release"
tar -xzf "$bundle" -C "$release"
test -s "$release/backend/target/world-0.1.0.jar"
test -s "$release/frontend/dist/index.html"

install -m 644 "$release/backend/target/world-0.1.0.jar" /opt/qingyu/game.jar
systemctl restart qingyu
healthy=0
for attempt in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18080/api/health >/dev/null 2>&1; then healthy=1; break; fi
  sleep 1
done
if [ "$healthy" != 1 ]; then
  install -m 644 "$backup/game.jar" /opt/qingyu/game.jar
  systemctl restart qingyu
  echo "New server failed health check. Previous JAR restored; database backup: $backup" >&2
  exit 1
fi

cp -r "$release/frontend/dist/assets/." /opt/qingyu/frontend/assets/
chmod -R a+rX /opt/qingyu/frontend/assets
cp "$release/frontend/dist/index.html" /opt/qingyu/frontend/index.html.next
chmod 644 /opt/qingyu/frontend/index.html.next
mv /opt/qingyu/frontend/index.html.next /opt/qingyu/frontend/index.html
systemctl is-active qingyu
curl -fsS http://127.0.0.1:18080/api/health
echo "Backup: $backup"
