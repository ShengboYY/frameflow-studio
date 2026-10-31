#!/usr/bin/env python3
"""IP-16: extend the disposable IP-15 environment with joint API regression.

Optional --browser-review keeps this same environment alive for up to 45 minutes.
Its ignored, owner-only connection file contains disposable test credentials and
is removed during cleanup. Create browser-review.done to finish the review.
"""

import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import signal
import sys
import time
import urllib.parse


spec = importlib.util.spec_from_file_location("compose_smoke", Path(__file__).with_name("compose-smoke.py"))
smoke_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke_module)
require = smoke_module.require


def write_private_json(path, value):
    # Exclusive creation protects another active review's connection file.
    with open(path, "x", encoding="utf-8",
              opener=lambda name, flags: os.open(name, flags, 0o600)) as handle:
        os.chmod(path, 0o600)
        try:
            json.dump(value, handle)
        except Exception:
            path.unlink(missing_ok=True)
            raise


class Regression(smoke_module.Smoke):
    def __init__(self, browser_review=False):
        super().__init__("ip16-regression.log")
        self.browser_review = browser_review
        self.review_file = smoke_module.ROOT / "test-results/ip16-browser.json"
        self.done_file = smoke_module.ROOT / "test-results/browser-review.done"
        self.owns_review = False
        # Smoke deliberately changes these during its persistence check.
        self.password_a = self.env["DEMO_A_PASSWORD"]
        self.password_b = self.env["DEMO_B_PASSWORD"]
        self.covered = set()

    def request(self, client, path, status=200, **kwargs):
        response = super().request(client, path, status, **kwargs)
        route = path.split("?", 1)[0]
        route = re.sub(r"/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(?=/|$)", "/{id}", route)
        if route.startswith("/api/v1/demo-media/"):
            route = "/api/v1/demo-media/{key}"
        elif route.startswith("/api/v1/templates/") and route != "/api/v1/templates/categories":
            route = "/api/v1/templates/{id}"
        method = kwargs.get("method") or ("POST" if kwargs.get("data") is not None else "GET")
        self.covered.add((method, route))
        return response

    def upload(self, client, template_id, source, status=201):
        boundary = "regression-" + secrets.token_hex(16)
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"templateId\"\r\n\r\n"
                f"{template_id}\r\n--{boundary}\r\n"
                'Content-Disposition: form-data; name="file"; filename="regression.jpg"\r\n'
                'Content-Type: image/jpeg\r\n\r\n').encode() + source + f"\r\n--{boundary}--\r\n".encode()
        headers = self.csrf(client)
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        response, _ = self.request(client, "/api/v1/assets", status, data=body, headers=headers)
        return json.loads(response)

    def submit(self, client, asset_ids, *, template="portrait-card", fail=None, delay=0, status=202):
        headers = self.csrf(client)
        headers["Content-Type"] = "application/json"
        body = json.dumps({"templateId": template, "assetIds": asset_ids,
                           "simulation": {"failAtStage": fail, "stageDelayMs": delay}}).encode()
        response, location = self.request(client, "/api/v1/tasks", status, data=body, headers=headers)
        value = json.loads(response)
        if status == 202:
            require(location.get("Location") == "/api/v1/tasks/" + value["taskId"], "Invalid acceptance Location")
        return value

    def terminal(self, client, task_id, *, fail=None):
        stages = ["CHECK_ASSETS", "GENERATE", "FINALIZE"]
        deadline = time.monotonic() + 65
        previous = None
        observed = []
        while time.monotonic() < deadline:
            task = self.get_json(client, "/api/v1/tasks/" + task_id)
            if previous:
                require(task["revision"] >= previous["revision"], "Revision moved backward")
                if task["currentStage"] and previous["currentStage"]:
                    require(stages.index(task["currentStage"]) >= stages.index(previous["currentStage"]),
                            "Stage moved backward")
            if task["currentStage"] and task["currentStage"] not in observed:
                observed.append(task["currentStage"])
            if task["status"] in ("SUCCEEDED", "FAILED"):
                if fail:
                    require(task["status"] == "FAILED" and task["currentStage"] == fail,
                            "Wrong failed stage")
                    require(task["result"] is None and task["failure"]["kind"] == "STAGE"
                            and task["failure"]["stage"] == fail
                            and task["failure"]["code"] == "MOCK_STAGE_FAILURE"
                            and task["failure"]["message"], "Invalid persisted failure")
                    require(task["revision"] == stages.index(fail) + 2, "Execution continued after failure")
                    self.request(client, "/api/v1/tasks/" + task_id + "/result", 409)
                else:
                    require(task["status"] == "SUCCEEDED" and task["revision"] == 4
                            and task["currentStage"] == "FINALIZE" and task["failure"] is None,
                            "Invalid success conclusion")
                    self.image(client, task["result"]["previewUrl"])
                for _ in range(2):
                    require(self.get_json(client, "/api/v1/tasks/" + task_id) == task,
                            "Terminal read changed persisted task")
                self.log(f"Task {task_id}: {task['status']}/{task['currentStage']}, revision={task['revision']}; observed={observed}")
                return task
            previous = task
            time.sleep(0.1)
        raise RuntimeError("Regression task timed out")

    def run(self):
        super().run()
        a, owner_a = self.login("demo-a", self.password_a)
        b, owner_b = self.login("demo-b", self.password_b)
        self.get_json(a, "/api/v1/templates/categories")
        filtered = self.get_json(a, "/api/v1/templates?categoryId=travel")
        require([item["templateId"] for item in filtered["items"]] == ["travel-postcard"], "Category filter failed")
        source = self.image(a, "/api/v1/demo-media/portrait-cover")
        asset = self.upload(a, "portrait-card", source)
        self.image(a, asset["previewUrl"], source)
        ids = [asset["assetId"]]

        self.log("[R1] Three stage failures and normal task accepted together")
        started = time.monotonic()
        success = self.submit(a, ids, delay=1200)
        self.log(f"Acceptance returned in {time.monotonic() - started:.3f}s; configured stage work totals 3.6s")
        initial = self.get_json(a, "/api/v1/tasks/" + success["taskId"])
        require(initial["status"] in ("PENDING", "RUNNING"), "Expected unfinished acceptance")
        self.request(a, "/api/v1/tasks/" + success["taskId"] + "/result", 409)
        failures = [(stage, self.submit(a, ids, fail=stage, delay=150))
                    for stage in ("CHECK_ASSETS", "GENERATE", "FINALIZE")]
        completed = [self.terminal(a, accepted["taskId"], fail=stage) for stage, accepted in failures]
        successful = self.terminal(a, success["taskId"])
        require(time.monotonic() - started >= 3.6, "Stage duration control ignored")
        require(len({task["taskId"] for task in completed + [successful]}) == 4, "Tasks not independent")

        self.log("[R2] No client task reads: SQL observes autonomous completion, then fresh login recovers history")
        unattended = self.submit(a, ids, delay=250)
        task_id = unattended["taskId"]
        deadline = time.monotonic() + 15
        while self.sql(f"SELECT status FROM production_task WHERE task_id='{task_id}'") != "SUCCEEDED":
            require(time.monotonic() < deadline, "Task needs client reads to advance")
            time.sleep(0.2)
        a, restored = self.login("demo-a", self.password_a)
        require(restored == owner_a, "Restored identity changed")
        history = self.get_json(a, "/api/v1/tasks")["items"]
        require({task_id, success["taskId"], *(item["taskId"] for _, item in failures)}
                <= {task["taskId"] for task in history}, "Refresh lost accepted tasks")
        self.terminal(a, task_id)

        self.log("[R3] Two-image template, owner isolation and distinct packaged results")
        postcard = self.get_json(a, "/api/v1/templates/travel-postcard")
        require(postcard["requirements"]["requiredImageCount"] == 2, "Wrong two-image requirements")
        second = self.upload(a, "travel-postcard", source)
        travel = self.submit(a, [second["assetId"], second["assetId"]], template="travel-postcard")
        travel_task = self.terminal(a, travel["taskId"])
        require(self.image(a, travel_task["result"]["previewUrl"]) !=
                self.image(a, successful["result"]["previewUrl"]), "Templates share wrong result")
        self.request(b, asset["previewUrl"], 404)
        self.request(b, "/api/v1/tasks/" + task_id, 404)
        self.request(b, successful["result"]["previewUrl"], 404)
        require(self.get_json(b, "/api/v1/tasks")["items"] == [], "B history includes A tasks")
        own_b = self.upload(b, "portrait-card", source)
        b_task = self.submit(b, [own_b["assetId"]])
        require(self.terminal(b, b_task["taskId"])["ownerId"] == owner_b["ownerId"], "B owns wrong task")
        self.request(a, "/api/v1/tasks/" + b_task["taskId"], 404)

        self.log("[R4] Input rejection does not create failed tasks; old token rejected under changed Cookie")
        before = self.sql("SELECT COUNT(*) FROM production_task")
        self.submit(a, [], status=422)
        self.submit(a, ids, delay=30001, status=400)
        self.submit(a, [own_b["assetId"]], status=404)
        self.upload(a, "portrait-card", b"not an image", status=415)
        self.upload(a, "portrait-card", source[:40], status=422)
        self.upload(a, "portrait-card", source + b"x" * (5 * 1024 * 1024), status=413)
        require(self.sql("SELECT COUNT(*) FROM production_task") == before, "Rejected input created a task")
        old = self.csrf(a)
        self.request(a, "/api/v1/auth/logout", 204, data=b"", headers=old)
        self.request(a, "/api/v1/auth/session", 401)
        headers = self.csrf(a)
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        self.request(a, "/api/v1/auth/login", data=urllib.parse.urlencode(
            {"username": "demo-b", "password": self.password_b}).encode(), headers=headers)
        old["Content-Type"] = "application/json"
        self.request(a, "/api/v1/tasks", 403, data=json.dumps(
            {"templateId": "portrait-card", "assetIds": [own_b["assetId"]]}).encode(), headers=old)
        self.request(a, "/api/v1/tasks/" + task_id, 404)
        expected = {("POST", "/api/v1/auth/login"), ("GET", "/api/v1/auth/session"),
                    ("GET", "/api/v1/templates/categories"), ("GET", "/api/v1/templates"),
                    ("GET", "/api/v1/templates/{id}"), ("GET", "/api/v1/demo-media/{key}"),
                    ("POST", "/api/v1/assets"), ("GET", "/api/v1/assets/{id}/content"),
                    ("POST", "/api/v1/tasks"), ("GET", "/api/v1/tasks"),
                    ("GET", "/api/v1/tasks/{id}"), ("GET", "/api/v1/tasks/{id}/result"),
                    ("POST", "/api/v1/auth/logout"), ("GET", "/api/v1/auth/csrf")}
        require(expected <= self.covered, f"API coverage missing: {sorted(expected - self.covered)}")
        self.log("[R5] PASS: 14 API methods/paths exercised across IP-15 + joint regression")

        if self.browser_review:
            compose_env = {key: self.env[key] for key in (
                "COMPOSE_PROJECT_NAME", "COMPOSE_PROFILES", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
                "MYSQL_ROOT_PASSWORD", "DEMO_A_PASSWORD", "DEMO_B_PASSWORD", "DB_PORT", "SERVER_PORT",
                "FRONTEND_PORT", "SESSION_COOKIE_SECURE", "TASK_WORKER_COUNT", "TASK_QUEUE_CAPACITY",
                "MOCK_CONTROLS_ENABLED")}
            write_private_json(self.review_file, {
                "base": self.base, "project": self.project, "compose": self.compose,
                "env": compose_env, "passwordA": self.password_a, "passwordB": self.password_b,
                "source": str(smoke_module.ROOT / "backend/src/main/resources/demo-media/portrait-cover.jpg")})
            self.owns_review = True
            self.done_file.unlink(missing_ok=True)
            self.log("Browser review ready: test-results/ip16-browser.json (private); waiting for browser-review.done")
            deadline = time.monotonic() + 45 * 60
            while not self.done_file.exists():
                require(time.monotonic() < deadline, "Browser review deadline reached")
                time.sleep(1)
            self.log("Browser review released; see separate browser evidence for actual assertions")

    def cleanup(self):
        if self.owns_review:
            self.review_file.unlink(missing_ok=True)
            self.done_file.unlink(missing_ok=True)
        return super().cleanup()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser-review", action="store_true")
    args = parser.parse_args()
    regression = Regression(args.browser_review)
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    status = 1
    try:
        regression.run()
        status = 0
    except (Exception, KeyboardInterrupt) as error:
        regression.log(f"FAIL: {type(error).__name__}: {error}")
        if regression.owned:
            regression.dc("ps", "--all", check=False, show=True)
            regression.dc("logs", "--no-color", "--tail", "100", check=False, show=True)
    finally:
        try:
            if not regression.cleanup():
                regression.log("FAIL: project cleanup incomplete")
                status = 1
        finally:
            regression.report.close()
    return status


if __name__ == "__main__":
    sys.exit(main())
