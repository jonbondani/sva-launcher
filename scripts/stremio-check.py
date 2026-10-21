#!/usr/bin/env python3
"""Checks the Stremio account used for "Estoy viendo" without a TV.

Runs in CI only when the STREMIO_EMAIL and STREMIO_PASSWORD repository secrets exist. It logs in,
reads the library the same way Sva does and prints counts and field names only: never titles,
ids or the session key, because CI logs of a public repository are public.
"""
import json
import os
import sys
import urllib.request

API = "https://api.strem.io/api/"


def call(method, body):
    request = urllib.request.Request(API + method, data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as reply:
        data = json.load(reply)
    if "error" in data:
        raise SystemExit(f"{method}: Stremio returned an error ({type(data['error']).__name__})")
    return data.get("result")


def main():
    email, password = os.environ.get("STREMIO_EMAIL", ""), os.environ.get("STREMIO_PASSWORD", "")
    if not email or not password:
        print("No STREMIO_EMAIL/STREMIO_PASSWORD secrets: skipping the account check.")
        return 0
    login = call("login", {"type": "Login", "email": email, "password": password})
    key = (login or {}).get("authKey")
    if not key:
        raise SystemExit("login: no session key in the reply")
    print("login: ok")
    library = call("datastoreGet", {"authKey": key, "collection": "libraryItem", "ids": [], "all": True})
    if not isinstance(library, list):
        raise SystemExit("datastoreGet: the reply is not a list")
    fields, in_progress, series = set(), 0, 0
    for item in library:
        state = item.get("state") or {}
        fields.update(f"state.{k}" for k in state)
        fields.update(k for k in item if k != "state")
        offset, duration = state.get("timeOffset") or 0, state.get("duration") or 0
        removed = item.get("removed") and not item.get("temp")
        if offset > 0 and not removed and (not duration or offset / duration < 0.92):
            in_progress += 1
            series += 1 if item.get("type") == "series" else 0
    print(f"library: {len(library)} items, {in_progress} in progress ({series} series)")
    needed = {"_id", "type", "name", "state.timeOffset", "state.duration", "state.lastWatched"}
    missing = sorted(needed - fields) if library else []
    has_video = "state.video_id" in fields or "state.videoId" in fields
    print("fields used by Sva:", "all present" if not missing and (has_video or not library) else f"missing {missing}")
    try:
        call("logout", {"authKey": key})   # end the CI session; not critical if it fails
    except (SystemExit, Exception):
        pass
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
