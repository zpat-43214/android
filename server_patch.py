# ── Paste into app.py, right AFTER the existing @app.route("/api/radio/token") function ──
# Lets the Android overlay app (paired via the existing 6-digit code -> device token) join
# radio channels without a browser session. Uses names that already exist in app.py:
# _device_tokens, _device_tokens_lock, _presence_lock, _online, _sid_username,
# _broadcast_presence, RADIO_CHANNELS, LIVEKIT_*, _build_livekit_token.

def _device_username():
    """Username for a valid 'Authorization: Bearer <device_token>' header, else None."""
    h = request.headers.get("Authorization", "")
    if not h.startswith("Bearer "):
        return None
    with _device_tokens_lock:
        return _device_tokens.get(h[7:].strip())


@app.route("/api/radio/device/channels")
def radio_device_channels():
    if not _device_username():
        return jsonify({"error": "Unauthorized"}), 401
    return jsonify({"channels": [
        {"key": k, "label": v["label"], "ptt": v["ptt"]} for k, v in RADIO_CHANNELS.items()
    ]})


@app.route("/api/radio/device/token", methods=["POST"])
def radio_device_token():
    username = _device_username()
    if not username:
        return jsonify({"error": "Unauthorized"}), 401

    channel = ((request.get_json(silent=True) or {}).get("channel") or "").strip()
    if channel not in RADIO_CHANNELS:
        return jsonify({"error": "Unknown channel"}), 400
    if not LIVEKIT_API_KEY or LIVEKIT_API_KEY.startswith("YOUR_"):
        return jsonify({"error": "Radio isn't configured yet"}), 503

    with _presence_lock:
        display = _online.get(username, {}).get("display_name") or username

    # "-mobile" suffix so it never collides with the same person's browser session in LiveKit
    token = _build_livekit_token(f"{username}-mobile", display, channel)
    return jsonify({"token": token, "url": LIVEKIT_URL, "channel": channel})


@socketio.on("device_radio_channel")
def on_device_radio_channel(data):
    """App tells the server which channel it's on, so the dispatcher sidebar shows it."""
    username = _sid_username.get(request.sid)
    if not username:
        return
    channel = (data or {}).get("channel")
    if channel is not None and channel not in RADIO_CHANNELS:
        return
    with _presence_lock:
        entry = _online.get(username)
        if entry:
            entry["channel"] = channel
    _broadcast_presence()
