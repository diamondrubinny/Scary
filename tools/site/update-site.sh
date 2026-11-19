#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
#
# Run on the server of scary.network (it asks for sudo):   bash update-site.sh
# Updates scary.network on its own server: the page from the repository's main branch, and the nginx
# Content-Security-Policy line that goes with it. Keeps a backup of both, and puts nginx's file back if its test fails.
set -euo pipefail
RAW=https://raw.githubusercontent.com/diamondrubinny/Scary/main/site
SUM=9374fcd8d864352036329c893585e409dfcb48cc9c9fc2dea4f1c1c723c39626
NGINX_DIR=${NGINX_DIR:-/etc/nginx}
STAMP=$(date +%Y%m%d-%H%M%S)
WORK=$(mktemp -d)
cd "$WORK"

curl -fsSLO "$RAW/index.html"
curl -fsSLO "$RAW/nginx-csp.conf"
echo "$SUM  index.html" | sha256sum -c -

# The nginx file with the CSP line, and the folder the site is served from (set CONF= and ROOT= to choose yourself).
if [ -z "${CONF:-}" ]; then
  mapfile -t found < <(sudo grep -rlE '^[[:space:]]*add_header[[:space:]]+Content-Security-Policy' "$NGINX_DIR" | sort -u)
  [ "${#found[@]}" -eq 1 ] || { echo "Found ${#found[@]} files with a CSP line: ${found[*]:-none}. Run again with CONF=/path/to/file"; exit 1; }
  CONF=${found[0]}
fi
if [ -z "${ROOT:-}" ]; then
  ROOT=$(sudo grep -rhE '^[[:space:]]*root[[:space:]]' $(sudo grep -rlE 'server_name[^;]*scary\.network' "$NGINX_DIR") | head -1 | awk '{print $2}' | tr -d ';')
fi
[ -n "$ROOT" ] && sudo test -f "$ROOT/index.html" || { echo "No index.html in '${ROOT:-}'. Run again with ROOT=/path/to/the/site"; exit 1; }
echo "nginx file: $CONF"
echo "site folder: $ROOT"

# Backups go to the home folder: not into the site (they would be served) nor next to nginx's file (it would be read).
PAGE_BAK="$HOME/scary-index.html.bak-$STAMP"
CONF_BAK="$HOME/scary-nginx-$(basename "$CONF").bak-$STAMP"
sudo cp -a "$ROOT/index.html" "$PAGE_BAK"
sudo cp -a "$CONF" "$CONF_BAK"
NEW=$(grep '^add_header' nginx-csp.conf)
sudo sed -i -E "s|^([[:space:]]*)add_header[[:space:]]+Content-Security-Policy[[:space:]].*\$|\1$NEW|" "$CONF"
[ "$(sudo grep -c 'HGsIL5Z4Fc9eqNqoB9hODW060fOWbQg6Wt+GVpQHVh0=' "$CONF")" -eq 1 ] || { sudo cp -a "$CONF_BAK" "$CONF"; echo "The CSP line was not replaced; nginx's file was put back."; exit 1; }
if ! sudo nginx -t; then
  sudo cp -a "$CONF_BAK" "$CONF"
  echo "nginx's test failed; its file was put back and nothing was reloaded."
  exit 1
fi
# Written over the existing file, so its owner and permissions stay as they were.
sudo cp "$WORK/index.html" "$ROOT/index.html"
sudo systemctl reload nginx

echo "Done. Backups: $PAGE_BAK and $CONF_BAK"
echo "To go back: sudo cp '$PAGE_BAK' '$ROOT/index.html' && sudo cp '$CONF_BAK' '$CONF' && sudo nginx -t && sudo systemctl reload nginx"
echo "Live check:"
curl -fsS https://scary.network/ | sha256sum | grep -q "$SUM" && echo "  page: the new one" || echo "  page: NOT the new one yet"
curl -fsSI https://scary.network/ | grep -qi 'HGsIL5Z4Fc9eqNqoB9hODW060fOWbQg6Wt+GVpQHVh0=' && echo "  CSP: matches the page" || echo "  CSP: does NOT match yet"
