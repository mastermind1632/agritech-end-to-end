#!/bin/sh
set -eu

cd "$HOME/agritech-end-to-end"
backup=".releases/$RELEASE_SHA"
test -f "$backup/.env"
chmod 600 .env

compose() {
    if [ "${RELEASE_HTTPS:-false}" = true ]; then
        docker compose --env-file .env -f docker-compose.prod.yml -f docker-compose.ai.yml \
            -f docker-compose.segmentation.yml -f docker-compose.https.yml "$@"
    else
        docker compose --env-file .env -f docker-compose.prod.yml -f docker-compose.ai.yml \
            -f docker-compose.segmentation.yml "$@"
    fi
}

rollback() {
    result=$?
    if [ "$result" -eq 0 ]; then return; fi
    trap - EXIT
    set +e
    echo '===== Release failed. Container status and the last backend log lines follow.'
    compose ps -a
    compose logs --no-color --tail 150 spring-app
    echo 'Release failed; restoring the previous application configuration. Database volumes are unchanged.'
    compose stop segmentation-service segmentation-worker
    for file in .env docker-compose.prod.yml docker-compose.ai.yml docker-compose.segmentation.yml docker-compose.https.yml; do
        if [ -f "$backup/$file" ]; then cp -p "$backup/$file" "$file"; fi
    done
    if [ -f "$backup/docker-compose.segmentation.yml" ]; then
        compose up -d --no-build --wait --wait-timeout 300
    else
        docker compose --env-file .env -f docker-compose.prod.yml -f docker-compose.ai.yml \
            up -d --no-build --wait --wait-timeout 300
    fi
    exit "$result"
}
trap rollback EXIT

printf '%s' "$GHCR_PAT" | docker login ghcr.io -u "$DOCKER_REGISTRY_OWNER" --password-stdin
compose config --quiet
available=$(df -Pk / | awk 'NR==2 {print $4}')
if [ "$available" -lt 4194304 ]; then
    echo 'Less than 4 GiB is free. Refusing to pull images into a nearly full disk.'
    exit 1
fi
compose pull --quiet
compose up -d --no-build --wait --wait-timeout 900

compose exec -T segmentation-service python - <<'PY'
import json
import time
from urllib.error import URLError
from urllib.request import urlopen

# Compose only checks process state for Spring, which has no container healthcheck.
for attempt in range(120):
    try:
        with urlopen('http://spring-app:8080/actuator/health', timeout=5) as response:
            if json.load(response).get('status') == 'UP':
                print('Spring backend readiness confirmed.')
                break
    except (URLError, TimeoutError, OSError, ValueError):
        pass
    time.sleep(2)
else:
    raise RuntimeError('Spring backend did not become ready before authenticated release checks')
PY

compose exec -T segmentation-service python /app/app/production_smoke.py
compose exec -T langchain-service python - <<'PY'
import json
from urllib.request import Request, urlopen
with urlopen('http://spring-app:8080/actuator/health', timeout=30) as response:
    assert json.load(response)['status'] == 'UP'
body = {'model': 'qwen2.5:0.5b', 'system': 'Give a short farming answer using the supplied fact.',
        'prompt': 'Crop seed buyers should check germination. What should they check?',
        'options': {'num_predict': 60, 'temperature': 0}}
request = Request('http://127.0.0.1:8001/api/generate', data=json.dumps(body).encode(),
                  headers={'Content-Type': 'application/json'})
with urlopen(request, timeout=240) as response:
    assert json.load(response)['response'].strip(), 'AI returned an empty answer'
print('Existing grounded Qwen assistant passed real generation.')
PY
if [ "${RELEASE_HTTPS:-false}" = true ]; then
    curl --fail --silent --show-error --max-time 30 --resolve 84.8.135.181:8080:127.0.0.1 \
        https://84.8.135.181:8080/actuator/health
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:8080/)
    test "$status" = 308
    sh scripts/install-certificate-renewal.sh
else
    curl --fail --silent --show-error --max-time 30 http://127.0.0.1:8080/actuator/health
    echo 'HTTP test deployment only. Do not enter real GPS, banking or other sensitive information.'
fi
df -h /
echo 'Live segmentation and original assistant verified.'
trap - EXIT
