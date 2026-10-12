import importlib.util
import io
import os
import tarfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

spec = importlib.util.spec_from_file_location('acme_hook', Path(__file__).parents[1] / 'acme_hook.py')
hook = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hook)


class HookTest(unittest.TestCase):
    def setUp(self):
        self.client = MagicMock()
        self.frontend = self.client.containers.get.return_value
        self.frontend.name = hook.CHALLENGE_CONTAINER
        self.frontend.exec_run.return_value.exit_code = 0
        self.frontend.put_archive.return_value = True

    def test_publish_only_one_safe_public_token_file(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'public.validation'
        with patch.dict(os.environ, {'CERTBOT_IDENTIFIER': hook.IP, 'CERTBOT_TOKEN': 'safe-token', 'CERTBOT_VALIDATION': 'public.validation'}), patch.object(hook, 'urlopen', return_value=response):
            hook.run('publish', self.client)
        root, payload = self.frontend.put_archive.call_args.args
        self.assertEqual(root, hook.WEBROOT)
        with tarfile.open(fileobj=io.BytesIO(payload)) as archive:
            self.assertEqual(archive.getnames(), ['safe-token'])
        self.assertEqual(self.frontend.exec_run.call_args.args[0], ['mkdir', '-p', hook.WEBROOT])

    def test_rejects_path_traversal_and_other_identifiers(self):
        for identifier, token in [(hook.IP, '../nginx.conf'), ('another.example', 'safe-token')]:
            with patch.dict(os.environ, {'CERTBOT_IDENTIFIER': identifier, 'CERTBOT_TOKEN': token}), self.assertRaises(ValueError):
                hook.run('cleanup', self.client)
        self.frontend.exec_run.assert_not_called()

    def test_cleanup_removes_only_its_token(self):
        with patch.dict(os.environ, {'CERTBOT_IDENTIFIER': hook.IP, 'CERTBOT_TOKEN': 'safe-token'}):
            hook.run('cleanup', self.client)
        self.frontend.exec_run.assert_called_once_with(['rm', '-f', hook.WEBROOT + '/safe-token'])

    def test_reload_is_limited_to_this_project_frontend(self):
        self.frontend.labels = {'com.docker.compose.project': 'different-project'}
        with self.assertRaises(ValueError):
            hook.run('reload', self.client)
        self.frontend.labels = {'com.docker.compose.project': 'agritech-end-to-end'}
        hook.run('reload', self.client)
        self.assertEqual(self.frontend.exec_run.call_args.args[0], ['nginx', '-s', 'reload'])
