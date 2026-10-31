"""Focused checks for the smoke harness's HTTP failure and log boundaries."""

import contextlib
from email.message import Message
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import Mock, patch
import urllib.error
import urllib.response


spec = importlib.util.spec_from_file_location("compose_smoke", Path(__file__).with_name("compose-smoke.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SmokeBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.smoke = module.Smoke.__new__(module.Smoke)
        self.smoke.base = "http://127.0.0.1:12345"
        self.smoke.hidden = ["test-password", ""]
        self.smoke.report = io.StringIO()
        self.client = Mock()

    def response(self, code=200, private=True):
        headers = Message()
        if private:
            headers["Cache-Control"] = "no-store"
        headers["Set-Cookie"] = "FF_SESSION=; Path=/api"
        return urllib.response.addinfourl(io.BytesIO(b"{}"), headers, self.smoke.base, code)

    def test_get_and_post_methods_work_without_explicit_method(self):
        for data, expected_method in ((None, "GET"), (b"{}", "POST")):
            with self.subTest(method=expected_method):
                self.client.open.return_value = self.response()
                body, _ = self.smoke.request(self.client, "/api/v1/tasks", data=data)
                self.assertEqual(body, b"{}")
                self.assertEqual(self.client.open.call_args.args[0].get_method(), expected_method)

    def test_wrong_status_fails_with_method_and_path(self):
        self.client.open.return_value = self.response(503)
        with self.assertRaisesRegex(RuntimeError, "GET /api/v1/tasks: expected 200, received 503"):
            self.smoke.request(self.client, "/api/v1/tasks")

    def test_expected_http_error_still_checks_private_cache_policy(self):
        headers = Message()
        headers["Cache-Control"] = "no-store"
        self.client.open.side_effect = urllib.error.HTTPError(
            self.smoke.base, 401, "Unauthorized", headers, io.BytesIO(b"{}"))
        body, _ = self.smoke.request(self.client, "/api/v1/auth/session", 401)
        self.assertEqual(body, b"{}")
        self.client.open.side_effect = None
        self.client.open.return_value = self.response(private=False)
        with self.assertRaisesRegex(RuntimeError, "Missing no-store"):
            self.smoke.request(self.client, "/api/v1/tasks")

    def test_logs_redact_credentials_hashes_and_ignore_empty_cookie_values(self):
        encoded = "{bcrypt}$2a$12$" + "a" * 53
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.smoke.log(f"test-password FF_SESSION=secret-cookie; {encoded}")
        result = self.smoke.report.getvalue()
        self.assertEqual(output.getvalue(), result)
        for value in ("test-password", "secret-cookie", encoded):
            self.assertNotIn(value, result)
        self.assertEqual(result.count("[REDACTED]"), 2)
        self.assertIn("[REDACTED_BCRYPT]", result)

    def test_failure_returns_nonzero_and_collects_logs_before_cleanup(self):
        smoke = Mock(owned=True, report=io.StringIO())
        smoke.run.side_effect = RuntimeError("business smoke failed")
        smoke.cleanup.return_value = True
        with patch.object(module, "Smoke", return_value=smoke), patch.object(module.signal, "signal"):
            self.assertEqual(module.main(), 1)
        self.assertEqual(smoke.dc.call_count, 2)
        smoke.cleanup.assert_called_once()
        self.assertTrue(smoke.report.closed)


if __name__ == "__main__":
    unittest.main()
