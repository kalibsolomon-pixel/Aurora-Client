#!/usr/bin/env python3
"""Development-only v1 receiver. Launch a child; never print or persist its capability.

Example: python tools/activity_bridge_receiver.py -- ./gradlew.bat --no-daemon runClient
No listener code or fixtures are included in the mod JAR.
"""
from __future__ import annotations

import hmac
import json
import os
import secrets
import socket
import subprocess
import sys
import time
import uuid

HANDSHAKE_BYTES = 1024
ACTIVITY_BYTES = 4096
IO_TIMEOUT = 2.0
MAX_ATTEMPTS = 8
ACCEPTED = b'{"type":"accepted","schemaVersion":1}\n'
BOOTSTRAP_KEYS = ("AURORA_ACTIVITY_ENDPOINT", "AURORA_ACTIVITY_SESSION_ID",
                  "AURORA_ACTIVITY_CAPABILITY", "AURORA_ACTIVITY_PROTOCOL")


class ProtocolError(ValueError):
    pass


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ProtocolError("duplicate field")
        result[key] = value
    return result


def decode_frame(frame: bytes, limit: int) -> dict:
    if len(frame) > limit or not frame.endswith(b"\n") or b"\n" in frame[:-1] or b"\r" in frame:
        raise ProtocolError("invalid framing")
    try:
        value = json.loads(frame.decode("utf-8"), object_pairs_hook=unique_object,
                           parse_constant=lambda _: (_ for _ in ()).throw(ProtocolError("invalid number")))
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("invalid JSON") from None
    if not isinstance(value, dict):
        raise ProtocolError("object required")
    return value


def bounded_line(peer: socket.socket, limit: int, idle_timeout: float) -> bytes:
    # Idle can last throughout gameplay. Once a frame begins, its TOTAL budget is 2s.
    started = time.monotonic()
    peer.settimeout(idle_timeout)
    first = peer.recv(1)
    if not first:
        return b""
    frame = bytearray(first)
    deadline = (started if idle_timeout <= IO_TIMEOUT else time.monotonic()) + IO_TIMEOUT
    while frame[-1] != 10:
        if len(frame) >= limit:
            raise ProtocolError("frame exceeds limit")
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ProtocolError("frame deadline exceeded")
        peer.settimeout(remaining)
        ch = peer.recv(1)
        if not ch:
            raise ProtocolError("truncated frame")
        frame.extend(ch)
    return bytes(frame)


def display_text(value, limit):
    import unicodedata
    if not isinstance(value, str) or not 1 <= len(value) <= limit or value != value.strip():
        raise ProtocolError("invalid display string")
    if any(unicodedata.category(c) in {"Cc", "Cf", "Cs"} or c in "\u2028\u2029" for c in value):
        raise ProtocolError("invalid display characters")


class ReceiverSession:
    def __init__(self, session_id: str, capability: str):
        self.session_id = session_id
        self._capability = capability
        self.sequence = 0
        self.authenticated = False

    def authenticate(self, frame: bytes):
        message = decode_frame(frame, HANDSHAKE_BYTES)
        if set(message) != {"type", "schemaVersion", "sessionId", "capability"}:
            raise ProtocolError("invalid handshake fields")
        if (message["type"] != "hello" or type(message["schemaVersion"]) is not int
                or message["schemaVersion"] != 1 or message["sessionId"] != self.session_id):
            raise ProtocolError("unsupported handshake")
        token = message["capability"]
        if (not isinstance(token, str) or len(token) != 64
                or any(c not in "0123456789abcdef" for c in token)
                or self.authenticated or not hmac.compare_digest(token, self._capability)):
            raise ProtocolError("authentication rejected")
        self.authenticated = True
        self._capability = None

    def activity(self, frame: bytes) -> dict:
        if not self.authenticated:
            raise ProtocolError("authentication required")
        message = decode_frame(frame, ACTIVITY_BYTES)
        required = {"type", "schemaVersion", "sessionId", "sequence", "state"}
        if not required <= set(message) or set(message) - required - {"worldDisplayName", "serverDisplayName", "serverAddress"}:
            raise ProtocolError("invalid activity fields")
        if (message["type"] != "activity" or type(message["schemaVersion"]) is not int
                or message["schemaVersion"] != 1 or message["sessionId"] != self.session_id):
            raise ProtocolError("unsupported activity")
        sequence = message["sequence"]
        if type(sequence) is not int or not self.sequence < sequence <= 9223372036854775807:
            raise ProtocolError("stale or invalid sequence")
        if self.sequence == 0 and (sequence != 1 or message["state"] != "MAIN_MENU"):
            raise ProtocolError("initial main-menu snapshot required")
        allowed = {"MAIN_MENU": set(), "SINGLEPLAYER": {"worldDisplayName"},
                   "MULTIPLAYER": {"serverDisplayName", "serverAddress"}}
        state = message["state"]
        if not isinstance(state, str) or state not in allowed or not set(message) - required <= allowed[state]:
            raise ProtocolError("invalid activity state")
        for field in set(message) - required:
            display_text(message[field], 255 if field == "serverAddress" else 128)
        self.sequence = sequence
        return message


def launch(command: list[str]) -> int:
    if not command:
        raise SystemExit("Pass a child launch command after --.")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        listener.settimeout(1)
        session = ReceiverSession(str(uuid.uuid4()), secrets.token_hex(32))
        env = {k: v for k, v in os.environ.items() if k not in BOOTSTRAP_KEYS}
        env.update(dict(zip(BOOTSTRAP_KEYS, (f"127.0.0.1:{listener.getsockname()[1]}",
                                           session.session_id, session._capability, "1"))))
        child = subprocess.Popen(command, env=env)
        del env
        attempts = 0
        deadline = time.monotonic() + 120
        try:
            while child.poll() is None and time.monotonic() < deadline and attempts < MAX_ATTEMPTS:
                try:
                    peer, _ = listener.accept()
                except socket.timeout:
                    continue
                attempts += 1
                with peer:
                    try:
                        session.authenticate(bounded_line(peer, HANDSHAKE_BYTES, IO_TIMEOUT))
                    except (ProtocolError, OSError):
                        print("Bridge authentication rejected.", flush=True)
                        continue
                    listener.close()  # Exactly one authenticated child; no reconnect.
                    peer.sendall(ACCEPTED)
                    print("Bridge authenticated.", flush=True)
                    while True:
                        frame = bounded_line(peer, ACTIVITY_BYTES, 600)
                        if not frame:
                            print("Bridge closed; activity cleared.", flush=True)
                            break
                        activity = session.activity(frame)
                        fields = sorted(set(activity) - {"type", "schemaVersion", "sessionId", "sequence", "state"})
                        print(f"Activity {activity['sequence']}: {activity['state']} (identity fields: {','.join(fields) or 'none'})", flush=True)
                    break
            else:
                print("Bridge not connected; child remains independent.", flush=True)
        except (ProtocolError, OSError):
            print("Bridge failed; activity cleared. Child remains independent.", flush=True)
        finally:
            session._capability = None
        return child.wait()


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--":
        args = args[1:]
    sys.exit(launch(args))
