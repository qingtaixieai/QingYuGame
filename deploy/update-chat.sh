set -eu
stamp=$(date +%Y%m%d-%H%M%S)
backup=/opt/qingyu/backups/chat-$stamp
release=/opt/qingyu/releases/chat-$stamp
config=/www/server/panel/vhost/nginx/qingyu.conf
install -d -m 700 "$backup"
sudo -u postgres pg_dump worldgame -Fc > "$backup/world.dump"
cp /opt/qingyu/game.jar "$backup/game.jar"
cp "$config" "$backup/nginx.conf"
cp /opt/qingyu/frontend/index.html "$backup/index.html"
install -d "$release"
tar -xzf /home/ubuntu/qingyu-chat.tar.gz -C "$release"
install -m 644 "$release/nginx-production.conf" "$config"
if ! nginx -t; then cp "$backup/nginx.conf" "$config"; exit 1; fi
install -m 644 "$release/game.jar" /opt/qingyu/game.jar
systemctl restart qingyu
healthy=0
for attempt in $(seq 1 60); do
 if curl -fsS http://127.0.0.1:18080/api/health >/dev/null 2>&1; then healthy=1; break; fi
 sleep 1
done
if [ "$healthy" != 1 ]; then
 cp "$backup/game.jar" /opt/qingyu/game.jar
 cp "$backup/nginx.conf" "$config"
 systemctl restart qingyu
 echo 'Deployment failed; previous executable and nginx config restored. Database backup retained.'
 exit 1
fi
nginx -s reload
cp -r "$release/frontend/assets/." /opt/qingyu/frontend/assets/
cp "$release/frontend/index.html" /opt/qingyu/frontend/index.html.next
chmod -R a+rX /opt/qingyu/frontend/assets
chmod 644 /opt/qingyu/frontend/index.html.next
mv /opt/qingyu/frontend/index.html.next /opt/qingyu/frontend/index.html
systemctl is-active qingyu
curl -fsS http://127.0.0.1:18080/api/health
echo "Backup: $backup"
