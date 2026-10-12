import io
import os
import re
import sys
import tarfile
from urllib.request import Request, urlopen

import docker

IP = '84.8.135.181'
CHALLENGE_CONTAINER = 'agritech_lema_frontend'
FRONTEND_CONTAINER = 'agritech-end-to-end-frontend-1'
WEBROOT = '/usr/share/nginx/html/.well-known/acme-challenge'


def challenge_values():
    identifier = os.getenv('CERTBOT_IDENTIFIER', os.getenv('CERTBOT_DOMAIN', ''))
    token = os.environ['CERTBOT_TOKEN']
    if identifier != IP or not re.fullmatch(r'[A-Za-z0-9_-]{1,256}', token):
        raise ValueError('Certificate hook only accepts this server IP and a safe challenge token')
    return token, os.getenv('CERTBOT_VALIDATION', '')


def run(action, client=None):
    client = client or docker.from_env()
    if action == 'reload':
        frontend = client.containers.get(FRONTEND_CONTAINER)
        if frontend.labels.get('com.docker.compose.project') != 'agritech-end-to-end':
            raise ValueError('Unexpected frontend project')
        result = frontend.exec_run(['nginx', '-t'])
        if result.exit_code:
            raise RuntimeError('TLS nginx configuration check failed')
        result = frontend.exec_run(['nginx', '-s', 'reload'])
        if result.exit_code:
            raise RuntimeError('TLS nginx certificate reload failed')
        return
    token, validation = challenge_values()
    frontend = client.containers.get(CHALLENGE_CONTAINER)
    if frontend.name != CHALLENGE_CONTAINER:
        raise ValueError('Unexpected challenge container')
    if action == 'cleanup':
        result = frontend.exec_run(['rm', '-f', WEBROOT + '/' + token])
        if result.exit_code:
            raise RuntimeError('Challenge cleanup failed')
        return
    if action != 'publish' or not validation or len(validation) > 2048:
        raise ValueError('Invalid certificate hook action or validation value')
    result = frontend.exec_run(['mkdir', '-p', WEBROOT])
    if result.exit_code:
        raise RuntimeError('Challenge directory could not be created')
    archive = io.BytesIO()
    with tarfile.open(fileobj=archive, mode='w') as tar:
        payload = validation.encode('ascii')
        info = tarfile.TarInfo(token)
        info.size = len(payload)
        info.mode = 0o644
        tar.addfile(info, io.BytesIO(payload))
    if not frontend.put_archive(WEBROOT, archive.getvalue()):
        raise RuntimeError('Challenge could not be published')
    # Only public challenge content is written; the shared app is not reconfigured or restarted.
    request = Request('http://' + IP + '/.well-known/acme-challenge/' + token)
    with urlopen(request, timeout=20) as response:
        if response.read().decode('ascii') != validation:
            raise RuntimeError('Challenge webroot does not serve the expected validation value')


if __name__ == '__main__':
    run(sys.argv[1])
