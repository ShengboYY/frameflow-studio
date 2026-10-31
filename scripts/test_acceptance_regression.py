"""Failure checks for the IP-16 harness; these do not replace live acceptance."""

import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location("acceptance_regression", Path(__file__).with_name("acceptance-regression.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RegressionBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.regression = module.Regression.__new__(module.Regression)
        self.regression.log = Mock()
        self.regression.image = Mock()
        self.regression.request = Mock()

    def failed(self, stage="GENERATE", revision=3):
        return {"taskId": "task", "status": "FAILED", "currentStage": stage, "revision": revision,
                "result": None, "failure": {"kind": "STAGE", "stage": stage,
                "code": "MOCK_STAGE_FAILURE", "message": "Stage failed"}}

    def test_failure_at_a_later_stage_cannot_satisfy_requested_failure(self):
        self.regression.get_json = Mock(return_value=self.failed("FINALIZE", 4))
        with self.assertRaisesRegex(RuntimeError, "Wrong failed stage"):
            self.regression.terminal(None, "task", fail="GENERATE")

    def test_extra_persisted_transition_after_failure_is_rejected(self):
        self.regression.get_json = Mock(return_value=self.failed(revision=4))
        with self.assertRaisesRegex(RuntimeError, "Execution continued after failure"):
            self.regression.terminal(None, "task", fail="GENERATE")

    def test_terminal_read_regression_cannot_pass(self):
        task = self.failed()
        self.regression.get_json = Mock(side_effect=[task, {**task, "revision": 2}])
        with self.assertRaisesRegex(RuntimeError, "Terminal read changed persisted task"):
            self.regression.terminal(None, "task", fail="GENERATE")

    def test_unreadable_result_is_not_counted_as_successful_preview(self):
        task = {"status": "SUCCEEDED", "currentStage": "FINALIZE", "revision": 4,
                "failure": None, "result": {"previewUrl": "/api/v1/tasks/task/result"}}
        self.regression.get_json = Mock(return_value=task)
        self.regression.image.side_effect = RuntimeError("Unreadable result")
        with self.assertRaisesRegex(RuntimeError, "Unreadable result"):
            self.regression.terminal(None, "task")

    def test_browser_connection_is_owner_only(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "browser.json"
            module.write_private_json(path, {"passwordA": "disposable-test-value"})
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(json.loads(path.read_text()), {"passwordA": "disposable-test-value"})

    def test_an_existing_browser_connection_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "browser.json"
            path.write_text("another review")
            with self.assertRaises(FileExistsError):
                module.write_private_json(path, {"passwordA": "new-value"})
            self.assertEqual(path.read_text(), "another review")

    def test_default_run_cleanup_does_not_remove_another_reviews_files(self):
        with tempfile.TemporaryDirectory() as directory:
            self.regression.review_file = Path(directory) / "browser.json"
            self.regression.done_file = Path(directory) / "browser-review.done"
            self.regression.review_file.write_text("another review")
            self.regression.done_file.touch()
            self.regression.owns_review = False
            with patch.object(module.smoke_module.Smoke, "cleanup", return_value=True):
                self.assertTrue(self.regression.cleanup())
            self.assertEqual(self.regression.review_file.read_text(), "another review")
            self.assertTrue(self.regression.done_file.exists())


if __name__ == "__main__":
    unittest.main()
