set -eu
stamp=$(date +%Y%m%d-%H%M%S)
install -d -m 700 /opt/qingyu/backups
sudo -u postgres pg_dump worldgame -Fc > /opt/qingyu/backups/world-$stamp.dump
cp /opt/qingyu/game.jar /opt/qingyu/backups/game-$stamp.jar
install -d /opt/qingyu/releases/emotes-v1
 tar -xzf /home/ubuntu/qingyu-emotes.tar.gz -C /opt/qingyu/releases/emotes-v1
install -m 644 /opt/qingyu/releases/emotes-v1/game.jar /opt/qingyu/game.jar
systemctl restart qingyu
healthy=0
for attempt in $(seq 1 40); do
 if curl -fsS http://127.0.0.1:18080/api/health >/dev/null 2>&1; then healthy=1; break; fi
 sleep 1
done
if [ "$healthy" != 1 ]; then
 cp /opt/qingyu/backups/game-$stamp.jar /opt/qingyu/game.jar
 systemctl restart qingyu
 echo 'Deployment failed; previous executable restored.'
 exit 1
fi
cp -r /opt/qingyu/releases/emotes-v1/frontend/assets/. /opt/qingyu/frontend/assets/
cp /opt/qingyu/releases/emotes-v1/frontend/index.html /opt/qingyu/frontend/index.html.next
chmod -R a+rX /opt/qingyu/frontend/assets
chmod 644 /opt/qingyu/frontend/index.html.next
mv /opt/qingyu/frontend/index.html.next /opt/qingyu/frontend/index.html
systemctl is-active qingyu
curl -fsS http://127.0.0.1:18080/api/health
