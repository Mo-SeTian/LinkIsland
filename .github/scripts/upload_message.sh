#!/usr/bin/env bash
set -euo pipefail

: "${MESSAGE_PUSH_URL:?Missing MESSAGE_PUSH_URL}"
: "${MESSAGE_PUSH_API_TOKEN:?Missing MESSAGE_PUSH_API_TOKEN}"
: "${MESSAGE_PUSH_CHANNEL_ID:?Missing MESSAGE_PUSH_CHANNEL_ID}"
case "$MESSAGE_PUSH_URL" in
  http://*|https://*) ;;
  *) echo 'MESSAGE_PUSH_URL must use http:// or https://' >&2; exit 1 ;;
esac

shopt -s nullglob
apks=(artifacts/LinkIsland-*.apk)
[[ ${#apks[@]} == 2 && -f artifacts/build-notes.md ]] || { echo 'Expected two APKs and build notes' >&2; exit 1; }
upload_response="$(mktemp)"
trap 'rm -f "$upload_response"' EXIT
curl --fail --silent --show-error --connect-timeout 15 --max-time 600 \
  --proto '=http,https' \
  --header "Authorization: Bearer ${MESSAGE_PUSH_API_TOKEN}" \
  --form-string "channelId=${MESSAGE_PUSH_CHANNEL_ID}" \
  --form 'caption=<artifacts/build-notes.md' \
  --form "files=@${apks[0]};type=application/vnd.android.package-archive" \
  --form "files=@${apks[1]};type=application/vnd.android.package-archive" \
  --output "$upload_response" \
  "${MESSAGE_PUSH_URL%/}/api/upload"

python3 - "$upload_response" <<'PY'
import json
import os
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text())
message = data["message"]
assert message["channelId"] == os.environ["MESSAGE_PUSH_CHANNEL_ID"], "Unexpected upload channel"
expected = sorted(p.name for p in Path("artifacts").glob("LinkIsland-*.apk"))
assert sorted(f["name"] for f in message["files"]) == expected, "Server did not confirm both APKs"
print("Message upload confirmed: both APKs in one message.")
PY
