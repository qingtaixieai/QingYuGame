#!/usr/bin/env sh
set -eu
/usr/bin/nginx -t
/usr/bin/nginx -s reload
