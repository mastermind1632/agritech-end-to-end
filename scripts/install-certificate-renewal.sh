#!/bin/sh
set -eu
cd "$HOME/agritech-end-to-end"
user=$(id -un)
case "$user" in *[!a-zA-Z0-9_-]*|'') echo 'Unexpected service account name'; exit 1;; esac
case "$HOME" in /home/"$user"|/root) ;; *) echo 'Unexpected home directory'; exit 1;; esac
file=$(mktemp)
trap 'rm -f "$file"' EXIT
sed "s|@USER@|$user|g;s|@HOME@|$HOME|g" scripts/certificate-renewal.cron > "$file"
sudo install -o root -g root -m 644 "$file" /etc/cron.d/agritech-ip-certificate
sudo systemctl is-active --quiet cron || sudo systemctl start cron
echo 'Certificate renewal is scheduled twice daily under the deployment account.'
