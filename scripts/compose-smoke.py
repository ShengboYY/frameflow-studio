#!/usr/bin/env python3
"""IP-15: disposable Compose acceptance through the published frontend only.

Requires Python 3.10+ and Docker Compose v2. Never reads the developer's .env.
Only the randomly named project created by this process is removed.
"""

import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


class Smoke:
    def __init__(self, report_name="compose-smoke.log"):
        self.project = "frameflow-smoke-" + secrets.token_hex(8)
        self.env = os.environ.copy()
        self.env.update({
            "COMPOSE_PROJECT_NAME": self.project,
            "COMPOSE_PROFILES": "",
            "DB_NAME": "frameflow_smoke", "DB_USERNAME": "smoke",
            "DB_PASSWORD": secrets.token_hex(24),
            "MYSQL_ROOT_PASSWORD": secrets.token_hex(24),
            "DEMO_A_PASSWORD": secrets.token_hex(24),
            "DEMO_B_PASSWORD": secrets.token_hex(24),
            "DB_PORT": "0", "SERVER_PORT": "0", "FRONTEND_PORT": "0",
            "DB_POOL_SIZE": "5", "APP_LOG_LEVEL": "INFO",
            "SESSION_COOKIE_SECURE": "false", "TASK_WORKER_COUNT": "2",
            "TASK_QUEUE_CAPACITY": "64", "MOCK_CONTROLS_ENABLED": "true",
        })
        self.hidden = [self.env[key] for key in (
            "DB_PASSWORD", "MYSQL_ROOT_PASSWORD", "DEMO_A_PASSWORD", "DEMO_B_PASSWORD")]
        self.compose = ["docker", "compose", "--project-name", self.project,
                        "--env-file", str(ROOT / ".env.example"),
                        "--file", str(ROOT / "compose.yaml")]
        self.owned = False
        self.base = ""
        report_dir = ROOT / "test-results"
        report_dir.mkdir(exist_ok=True)
        self.report = (report_dir / report_name).open("w", encoding="utf-8")

    def log(self, message):
        for value in sorted({value for value in self.hidden if value}, key=len, reverse=True):
            message = message.replace(value, "[REDACTED]")
        message = re.sub(r"(?:\{bcrypt\})?\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}",
                         "[REDACTED_BCRYPT]", message)
        message = re.sub(r"FF_SESSION=[^;\s]+", "FF_SESSION=[REDACTED]", message)
        print(message, flush=True)
        self.report.write(message + "\n")
        self.report.flush()

    def command(self, args, *, check=True, show=False, timeout=180):
        result = subprocess.run(args, cwd=ROOT, env=self.env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=timeout, check=False)
        if show or (check and result.returncode):
            self.log(result.stdout)
        require(not check or result.returncode == 0,
                f"Command failed ({result.returncode}): {' '.join(args[:2])}")
        return result

    def dc(self, *args, **kwargs):
        return self.command(self.compose + list(args), **kwargs)

    def sql(self, query):
        return self.dc("exec", "-T", "mysql", "sh", "-c",
                       'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --host=127.0.0.1 '
                       '--user="$MYSQL_USER" --database="$MYSQL_DATABASE" '
                       '--batch --skip-column-names --execute "$1"',
                       "smoke-sql", query).stdout.strip()

    def client(self):
        # Explicitly bypass host proxy settings for this loopback-only check.
        return urllib.request.build_opener(urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, client, path, status=200, *, data=None, headers=None, method=None):
        require(path.startswith("/") and not path.startswith("//"), "Invalid API path")
        request = urllib.request.Request(self.base + path, data=data,
                                         headers=headers or {}, method=method)
        try:
            response = client.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            content = response.read()
            for cookie in response.headers.get_all("Set-Cookie", []):
                self.hidden.append(cookie.split(";", 1)[0].split("=", 1)[-1])
            require(response.status == status,
                    f"{request.get_method()} {path}: expected {status}, received {response.status}")
            if path.startswith("/api/v1/auth/") or path.startswith((
                    "/api/v1/tasks", "/api/v1/assets")):
                require("no-store" in response.headers.get("Cache-Control", ""),
                        f"Missing no-store: {path}")
            return content, response.headers

    def get_json(self, client, path):
        body, headers = self.request(client, path)
        require(headers.get_content_type() == "application/json", "Expected JSON")
        return json.loads(body)

    def csrf(self, client):
        token = self.get_json(client, "/api/v1/auth/csrf")
        require(token["headerName"] == "X-CSRF-TOKEN" and token["token"], "Invalid CSRF")
        self.hidden.append(token["token"])
        return {"X-CSRF-TOKEN": token["token"]}

    def login(self, username, password):
        client = self.client()
        headers = self.csrf(client)
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        body, _ = self.request(client, "/api/v1/auth/login", data=urllib.parse.urlencode(
            {"username": username, "password": password}).encode(), headers=headers)
        account = json.loads(body)
        require(account["username"] == username, "Wrong login identity")
        require(self.get_json(client, "/api/v1/auth/session") == account, "Session mismatch")
        return client, account

    def image(self, client, path, expected=None):
        body, headers = self.request(client, path)
        require(headers.get_content_type() == "image/jpeg", "Expected JPEG")
        require(headers.get("X-Content-Type-Options") == "nosniff", "Missing nosniff")
        require(body.startswith(b"\xff\xd8") and body.endswith(b"\xff\xd9"), "Invalid JPEG bytes")
        if expected is not None:
            require(body == expected, "Persisted media changed")
        return body

    def wait_frontend(self):
        address = self.dc("port", "frontend", "3000").stdout.strip()
        require(re.fullmatch(r"127\.0\.0\.1:\d+", address), "Expected loopback frontend port")
        self.base = "http://" + address
        deadline = time.monotonic() + 120
        while True:
            try:
                catalog = self.get_json(self.client(), "/api/v1/templates")
                require(len(catalog["items"]) >= 2, "Missing template catalog")
                self.request(self.client(), "/login")
                return
            except (OSError, RuntimeError, ValueError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("Frontend/Java HTTP readiness timed out") from None
                time.sleep(2)

    def verify_users(self):
        for service in ("backend", "frontend"):
            uid = self.dc("exec", "-T", service, "id", "-u").stdout.strip()
            require(uid.isdigit() and uid != "0", f"{service} runs as root")
        self.dc("exec", "-T", "backend", "sh", "-c",
                'test -w "$ASSET_STORAGE_ROOT"')

    def wait_task(self, client, task_id):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            task = self.get_json(client, "/api/v1/tasks/" + task_id)
            require(task["status"] != "FAILED", "Background task failed")
            if task["status"] == "SUCCEEDED":
                require(task["currentStage"] == "FINALIZE" and task["failure"] is None,
                        "Invalid task conclusion")
                return task
            time.sleep(0.25)
        raise RuntimeError("Background task timed out")

    def run(self):
        self.log(f"Isolated project: {self.project}")
        self.command(["docker", "info", "--format", "{{.ServerVersion}}"])
        for kind, subcommand in (("container", ["ps", "-aq"]), ("volume", ["volume", "ls", "-q"]),
                                 ("network", ["network", "ls", "-q"])):
            existing = self.command(["docker"] + subcommand + ["--filter",
                "label=com.docker.compose.project=" + self.project]).stdout.strip()
            require(not existing, f"Refusing to reuse existing {kind} resources")
        self.owned = True
        self.log("[1/6] Compose configuration")
        self.dc("config", "--quiet")
        self.log("[2/6] Build production images (no host application processes)")
        self.dc("build", show=True, timeout=1200)
        self.log("[3/6] Empty volumes: SQL readiness and explicit account initialization")
        self.dc("up", "-d", "--wait", "--wait-timeout", "180", show=True, timeout=240)
        require(self.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")
                == "4", "Expected four initialized tables")
        accounts = self.sql("SELECT username, account_id, password_hash FROM user_account ORDER BY username")
        require(len(accounts.splitlines()) == 2, "Expected two initialized accounts")
        self.verify_users()
        self.wait_frontend()
        self.log("[4/6] Frontend HTTP: CSRF, login, catalog, upload, submit, query and private media")
        a, account_a = self.login("demo-a", self.env["DEMO_A_PASSWORD"])
        b, _ = self.login("demo-b", self.env["DEMO_B_PASSWORD"])
        require(self.get_json(a, "/api/v1/tasks")["items"] == [], "Expected empty initial history")
        template = self.get_json(a, "/api/v1/templates/portrait-card")
        source = self.image(a, template["coverUrl"])
        boundary = "frameflow-" + secrets.token_hex(16)
        multipart = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"templateId\"\r\n\r\n"
            f"portrait-card\r\n--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; filename="smoke.jpg"\r\n'
            'Content-Type: image/jpeg\r\n\r\n').encode() + source + f"\r\n--{boundary}--\r\n".encode()
        headers = self.csrf(a)
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        body, _ = self.request(a, "/api/v1/assets", 201, data=multipart, headers=headers)
        asset = json.loads(body)
        require(asset["ownerId"] == account_a["ownerId"], "Asset owner mismatch")
        self.image(a, asset["previewUrl"], source)
        self.request(b, asset["previewUrl"], 404)
        payload = json.dumps({"templateId": "portrait-card", "assetIds": [asset["assetId"]],
                              "simulation": {"stageDelayMs": 100}}).encode()
        self.request(a, "/api/v1/tasks", 403, data=payload,
                     headers={"Content-Type": "application/json"})
        headers = self.csrf(a)
        headers["Content-Type"] = "application/json"
        body, response_headers = self.request(a, "/api/v1/tasks", 202, data=payload, headers=headers)
        accepted = json.loads(body)
        task_path = "/api/v1/tasks/" + accepted["taskId"]
        require(response_headers.get("Location") == task_path, "Missing task Location")
        task = self.wait_task(a, accepted["taskId"])
        result = self.image(a, task["result"]["previewUrl"])
        require(self.get_json(a, "/api/v1/tasks")["items"] == [task], "History mismatch")
        require(self.get_json(b, "/api/v1/tasks")["items"] == [], "Account history leaked")
        self.request(b, task_path, 404)
        self.request(b, task["result"]["previewUrl"], 404)
        self.request(self.client(), asset["previewUrl"], 401)
        self.request(self.client(), task["result"]["previewUrl"], 401)
        require(self.sql("SELECT COUNT(*) FROM uploaded_asset") == "1", "Missing stored asset")
        require(self.sql("SELECT COUNT(*) FROM production_task WHERE status='SUCCEEDED'") == "1",
                "Missing persisted success")
        require(self.sql("SELECT COUNT(*) FROM task_input_asset") == "1", "Missing persisted binding")
        self.log("[5/6] down/up retains both volumes; old session expires; original passwords/IDs persist")
        password_a, password_b = self.env["DEMO_A_PASSWORD"], self.env["DEMO_B_PASSWORD"]
        self.dc("down", show=True)
        # If either SQL or account initialization replays destructively, this restart fails.
        for key in ("DEMO_A_PASSWORD", "DEMO_B_PASSWORD"):
            self.env[key] = secrets.token_hex(24)
            self.hidden.append(self.env[key])
        self.dc("up", "-d", "--wait", "--wait-timeout", "180", show=True, timeout=240)
        require(self.sql("SELECT username, account_id, password_hash FROM user_account ORDER BY username")
                == accounts, "Existing account IDs/passwords changed")
        self.wait_frontend()
        self.verify_users()
        self.request(a, "/api/v1/auth/session", 401)
        a, restored = self.login("demo-a", password_a)
        b, _ = self.login("demo-b", password_b)
        require(restored == account_a, "Stable account changed")
        require(self.get_json(a, "/api/v1/tasks")["items"] == [task], "History lost on recreation")
        require(self.get_json(a, task_path) == task, "Task changed on recreation")
        self.image(a, asset["previewUrl"], source)
        self.image(a, task["result"]["previewUrl"], result)
        self.request(b, asset["previewUrl"], 404)
        self.request(b, task["result"]["previewUrl"], 404)
        self.request(a, "/api/v1/auth/logout", 204, data=b"", headers=self.csrf(a))
        self.request(a, "/api/v1/auth/session", 401)
        self.log("[6/6] PASS: initialization, business smoke, privacy and volume recreation")

    def cleanup(self):
        if not self.owned:
            return True
        self.log("Cleaning only this smoke project's containers, network, volumes and image tags")
        result = self.dc("down", "--volumes", "--remove-orphans", check=False, show=True)
        # Build cache and shared base images remain available to other projects.
        for tag in (self.project + "-backend", self.project + "-frontend"):
            if self.command(["docker", "image", "inspect", tag], check=False).returncode == 0:
                removed = self.command(["docker", "image", "rm", tag], check=False, show=True)
                if removed.returncode:
                    return False
        return result.returncode == 0


def main():
    smoke = Smoke()
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    status = 1
    try:
        smoke.run()
        status = 0
    except (Exception, KeyboardInterrupt) as error:
        smoke.log(f"FAIL: {type(error).__name__}: {error}")
        if smoke.owned:
            smoke.dc("ps", "--all", check=False, show=True)
            smoke.dc("logs", "--no-color", "--tail", "150", check=False, show=True)
    finally:
        try:
            if not smoke.cleanup():
                smoke.log("FAIL: project cleanup incomplete")
                status = 1
        finally:
            smoke.report.close()
    return status


if __name__ == "__main__":
    sys.exit(main())
