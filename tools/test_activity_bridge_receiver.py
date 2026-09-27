import json
import unittest
from activity_bridge_receiver import ReceiverSession, ProtocolError, decode_frame

SESSION = "4b609826-a9a8-4fa6-a7d4-57e7f373900e"
TOKEN = "ab" * 32  # Test fixture only; production capability is generated per launch.


def frame(**fields):
    return (json.dumps(fields, separators=(",", ":")) + "\n").encode()


class ReceiverTests(unittest.TestCase):
    def hello(self, **changes):
        return frame(**dict(type="hello", schemaVersion=1, sessionId=SESSION, capability=TOKEN) | changes)

    def activity(self, sequence=1, state="MAIN_MENU", **changes):
        return frame(**dict(type="activity", schemaVersion=1, sessionId=SESSION, sequence=sequence, state=state) | changes)

    def session(self):
        session = ReceiverSession(SESSION, TOKEN)
        session.authenticate(self.hello())
        return session

    def test_valid_authentication_discards_capability(self):
        session = self.session()
        self.assertTrue(session.authenticated)
        self.assertIsNone(session._capability)
        with self.assertRaises(ProtocolError):
            session.authenticate(self.hello())

    def test_bad_capability_session_and_version_rejected(self):
        for change in ({"capability": "cd" * 32}, {"capability": TOKEN + "x"}, {"sessionId": "wrong"},
                       {"schemaVersion": 2}, {"schemaVersion": True}, {"type": "activity"}, {"capability": []}):
            with self.subTest(change=change), self.assertRaises(ProtocolError):
                ReceiverSession(SESSION, TOKEN).authenticate(self.hello(**change))

    def test_activity_before_authentication_rejected(self):
        with self.assertRaises(ProtocolError):
            ReceiverSession(SESSION, TOKEN).activity(self.activity())

    def test_malformed_duplicate_utf8_oversized_frames_rejected(self):
        for raw in (b"nope\n", b"[]\n", b'{"x":1,"x":2}\n', b"\xff\n", b"{}", b"{}\n{}\n",
                    b'{"x":NaN}\n', b"{" + b"x" * 1024 + b"}\n", b"{}\r\n"):
            with self.subTest(raw=raw[:16]), self.assertRaises(ProtocolError):
                decode_frame(raw, 1024)

    def test_increasing_duplicate_and_stale_sequence(self):
        session = self.session()
        session.activity(self.activity())
        session.activity(self.activity(2, "SINGLEPLAYER", worldDisplayName="Fixture"))
        for sequence in (2, 1, 0, -1, True, 2.5, 9223372036854775808):
            with self.subTest(sequence=sequence), self.assertRaises(ProtocolError):
                session.activity(self.activity(sequence))
        session.activity(self.activity(3))

    def test_startup_requires_sequence_one_main_menu(self):
        for message in (self.activity(2), self.activity(1, "SINGLEPLAYER")):
            with self.assertRaises(ProtocolError):
                self.session().activity(message)

    def test_state_transitions_and_identity_categories(self):
        session = self.session()
        messages = (self.activity(), self.activity(2, "SINGLEPLAYER", worldDisplayName="Fixture"),
                    self.activity(3), self.activity(4, "MULTIPLAYER", serverDisplayName="A", serverAddress="a.invalid"),
                    self.activity(5), self.activity(6, "MULTIPLAYER", serverDisplayName="B", serverAddress="b.invalid"),
                    self.activity(7))
        for message in messages:
            session.activity(message)
        self.assertEqual(session.sequence, 7)

    def test_controls_and_oversized_names_rejected(self):
        for name in ("a\n", "a\x00", "a\u202e", "a\u2028", "a\ud800", "a" * 129, " ", [], None):
            session = self.session()
            session.activity(self.activity())
            with self.subTest(name=name), self.assertRaises(ProtocolError):
                session.activity(self.activity(2, "SINGLEPLAYER", worldDisplayName=name))

    def test_unavailable_identity_is_optional(self):
        session = self.session()
        session.activity(self.activity())
        session.activity(self.activity(2, "SINGLEPLAYER"))
        session.activity(self.activity(3, "MULTIPLAYER"))

    def test_unknown_fields_states_and_cross_category_identity_rejected(self):
        for changes in ({"state": "REALMS"}, {"worldDisplayName": "Fixture"}, {"command": "quit"},
                        {"schemaVersion": 2}, {"state": []}, {"sessionId": "wrong"},
                        {"state": "SINGLEPLAYER", "serverAddress": "a.invalid"}):
            session = self.session()
            session.activity(self.activity())
            with self.subTest(changes=changes), self.assertRaises(ProtocolError):
                session.activity(self.activity(2, **changes))


if __name__ == "__main__":
    unittest.main()
