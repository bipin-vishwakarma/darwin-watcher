import os
import sys
import json
import urllib.request
import urllib.parse
from pathlib import Path
from dotenv import load_dotenv

# Ensure UTF-8 output on Windows console
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

env_path = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

token = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
chat_id = os.getenv("TELEGRAM_CHAT_ID", "").strip()

if not token:
    print("[ERROR] TELEGRAM_BOT_TOKEN is empty in .env!")
    print(f"Please open {env_path} and set your TELEGRAM_BOT_TOKEN.")
    exit(1)

# Normalize token
clean_token = token[3:] if token.startswith("bot") else token

print(f"[*] Testing Telegram Bot Token: {clean_token[:8]}...{clean_token[-4:]}")

# 1. Test getMe
try:
    req = urllib.request.Request(f"https://api.telegram.org/bot{clean_token}/getMe")
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        if data.get("ok"):
            bot_info = data["result"]
            print(f"[OK] Bot Connected: @{bot_info.get('username')} ({bot_info.get('first_name')})")
        else:
            print(f"[ERROR] Telegram Error: {data}")
            exit(1)
except Exception as e:
    print(f"[ERROR] Failed to reach Telegram API: {e}")
    exit(1)

# 2. Check getUpdates to see recent messages and find chat_id
print("\n[*] Fetching latest updates from bot...")
detected_chat_id = None
try:
    req = urllib.request.Request(f"https://api.telegram.org/bot{clean_token}/getUpdates")
    with urllib.request.urlopen(req, timeout=10) as resp:
        updates = json.loads(resp.read().decode("utf-8"))
        if updates.get("ok") and updates["result"]:
            print("[*] Found recent chats:")
            for u in updates["result"][-5:]:
                msg = u.get("message") or u.get("channel_post") or {}
                chat = msg.get("chat", {})
                user = msg.get("from", {})
                c_id = chat.get("id")
                c_type = chat.get("type")
                name = user.get("first_name", chat.get("title", "Unknown"))
                print(f"  -> Name: {name} | Type: {c_type} | Chat ID: {c_id}")
                detected_chat_id = str(c_id)
        else:
            print("[INFO] No recent messages found. Tip: Open Telegram, search your bot, and send /start!")
except Exception as e:
    print(f"[WARN] Could not fetch updates: {e}")

target_chat_id = chat_id or detected_chat_id

# 3. Send test message if chat_id is available
if target_chat_id:
    print(f"\n[*] Sending test message to Chat ID: {target_chat_id}...")
    try:
        body = urllib.parse.urlencode({
            "chat_id": target_chat_id,
            "text": "Darwin Watcher: Bot is successfully configured and connected to your device!"
        }).encode("utf-8")
        req = urllib.request.Request(
            f"https://api.telegram.org/bot{clean_token}/sendMessage",
            data=body,
            headers={"Content-Type": "application/x-www-form-urlencoded"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            res = json.loads(resp.read().decode("utf-8"))
            if res.get("ok"):
                print("[SUCCESS] Test message sent to your Telegram successfully!")
                if not chat_id and detected_chat_id:
                    print(f"\n[ACTION] Please add TELEGRAM_CHAT_ID={detected_chat_id} into your .env file!")
            else:
                print(f"[ERROR] Failed to send message: {res}")
    except Exception as e:
        print(f"[ERROR] Error sending message: {e}")
else:
    print("\n[WARN] TELEGRAM_CHAT_ID is not set in .env yet.")
    print("Please send /start to your bot in Telegram and re-run this script!")
