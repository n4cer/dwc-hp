#!/usr/bin/env python3
"""Minimal privileged sidecar that lets the dwc admin panel start/stop the
Quake 3 docker-compose stacks without granting the dwc web process itself
any Docker or systemd privileges.

Runs as root under quake3ctl.service, bound to 127.0.0.1 only. It exposes
exactly three actions (status/start/stop) for a fixed, hardcoded allowlist
of systemd units, authenticated with a bearer token shared with the dwc
app via /etc/dwc/quake3ctl.env and /etc/dwc/environment. It never invokes
a shell and never accepts a unit name from the caller as raw text.
"""

import hmac
import http.server
import os
import subprocess
import sys

# name (as used in admin panel URLs) -> systemd unit it controls.
UNITS = {
    "duel": "quake3@duel.service",
    "tdm": "quake3@tdm.service",
    "ctf": "quake3@ctf.service",
}

TOKEN = os.environ.get("QUAKE3CTL_TOKEN", "")
PORT = int(os.environ.get("QUAKE3CTL_PORT", "8765"))


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "quake3ctl/1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _unauthorized(self):
        reason = "no token configured" if not TOKEN else "token mismatch"
        self._respond_text(401, "unauthorized: " + reason + "\n")

    def _authorized(self):
        if not TOKEN:
            return False
        header = self.headers.get("Authorization", "")
        prefix = "Bearer "
        if not header.startswith(prefix):
            return False
        return hmac.compare_digest(header[len(prefix):], TOKEN)

    def _respond_text(self, status, body):
        encoded = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _unit_or_404(self, name):
        unit = UNITS.get(name)
        if unit is None:
            self._respond_text(404, "unknown server\n")
        return unit

    def _is_active_state(self, unit):
        result = subprocess.run(
            ["/usr/bin/systemctl", "is-active", unit],
            capture_output=True, text=True, timeout=10,
        )
        return result.stdout.strip() or "unknown"

    def do_GET(self):
        if not self._authorized():
            return self._unauthorized()
        parts = self.path.strip("/").split("/")
        if len(parts) == 2 and parts[0] == "status":
            unit = self._unit_or_404(parts[1])
            if unit is None:
                return
            return self._respond_text(200, self._is_active_state(unit))
        self._respond_text(404, "not found\n")

    def do_POST(self):
        if not self._authorized():
            return self._unauthorized()
        parts = self.path.strip("/").split("/")
        if len(parts) == 2 and parts[0] in ("start", "stop"):
            unit = self._unit_or_404(parts[1])
            if unit is None:
                return
            try:
                subprocess.run(
                    ["/usr/bin/systemctl", parts[0], unit],
                    capture_output=True, text=True, timeout=120, check=True,
                )
            except subprocess.CalledProcessError as error:
                return self._respond_text(502, error.stderr or "systemctl failed\n")
            except subprocess.TimeoutExpired:
                return self._respond_text(504, "timed out\n")
            return self._respond_text(200, self._is_active_state(unit))
        self._respond_text(404, "not found\n")


def main():
    if not TOKEN:
        sys.exit("QUAKE3CTL_TOKEN must be set (see quake3ctl.env.example)")
    server = http.server.HTTPServer(("127.0.0.1", PORT), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
