#!/bin/sh
set -eu
cd "$HOME/agritech-end-to-end"
exec 9>.certificate-renew.lock
flock -n 9 || exit 0
docker compose --env-file .env -f docker-compose.prod.yml -f docker-compose.https.yml \
    run --rm --no-deps certbot renew --non-interactive \
    --deploy-hook 'python /hooks/acme_hook.py reload'
