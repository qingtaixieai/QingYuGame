#!/usr/bin/env bash
set -euo pipefail
install -d -m 755 /usr/share/keyrings
curl --fail --silent --show-error --max-time 30 https://www.postgresql.org/media/keys/ACCC4CF8.asc -o /usr/share/keyrings/postgresql-pgdg.asc
printf '%s\n' 'deb [signed-by=/usr/share/keyrings/postgresql-pgdg.asc] https://apt.postgresql.org/pub/repos/apt jammy-pgdg main' > /etc/apt/sources.list.d/qingyu-pgdg.list
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq postgresql-17
printf '%s\n' "listen_addresses = '127.0.0.1'" 'shared_buffers = 128MB' 'max_connections = 30' > /etc/postgresql/17/main/conf.d/qingyu.conf
systemctl restart postgresql@17-main
pg_isready -h 127.0.0.1 -p 5432
