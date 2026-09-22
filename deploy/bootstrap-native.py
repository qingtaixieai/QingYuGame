"""Root-only bootstrap for the verified Ubuntu host. Never prints generated secrets."""
import os
import pathlib
import pwd
import secrets
import subprocess

base = pathlib.Path('/opt/qingyu')
env_file = base / 'runtime.env'
if not env_file.exists():
    database_password = secrets.token_urlsafe(30)
    admin_password = secrets.token_urlsafe(21)
    values = dict(BIND_ADDRESS='127.0.0.1', PORT='18080',
                  DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/worldgame',
                  DATABASE_USER='worldgame', DATABASE_PASSWORD=database_password,
                  ADMIN_USERNAME='admin', ADMIN_PASSWORD=admin_password,
                  APP_ORIGIN='https://qingtaixieai.com', SECURE_COOKIE='true')
    fd = os.open(env_file, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'w') as f:
        f.write(''.join(f'{k}={v}\n' for k, v in values.items()))
    access = pathlib.Path('/home/ubuntu/qingyu-admin.txt')
    fd = os.open(access, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'w') as f:
        f.write(f'游戏地址：https://qingtaixieai.com\n管理员用户名：admin\n管理员密码：{admin_password}\n')
    owner = pwd.getpwnam('ubuntu')
    os.chown(access, owner.pw_uid, owner.pw_gid)
values = dict(line.split('=', 1) for line in env_file.read_text().splitlines() if '=' in line)

def psql(sql):
    return subprocess.run(['runuser','-u','postgres','--','psql','-X','-v','ON_ERROR_STOP=1','-At'],
                          input=sql,text=True,check=True,capture_output=True).stdout.strip()

if not psql("select 1 from pg_roles where rolname='worldgame';"):
    password = values['DATABASE_PASSWORD'].replace("'", "''")
    psql(f"create role worldgame login password '{password}' nosuperuser nocreatedb nocreaterole;")
if not psql("select 1 from pg_database where datname='worldgame';"):
    psql('create database worldgame owner worldgame;')
try:
    pwd.getpwnam('qingyu')
except KeyError:
    subprocess.run(['useradd','--system','--home-dir','/opt/qingyu','--shell','/usr/sbin/nologin','qingyu'],check=True)
print('Production database, service account and private credential files ready.')
