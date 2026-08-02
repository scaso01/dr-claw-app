#!/data/data/com.termux/files/usr/bin/sh
# install-device-bridge.sh — Phase 4 phone-as-device bridge installer.
#
# Run IN TERMUX on the phone:  ssh -p 8022 u0_a711@<phone> 'sh -s' < install-device-bridge.sh
#
# Idempotent. Sets up:
#   - proot loopback ssh key, authorized in Termux  (so the proot can reach Termux sshd:8022)
#   - $PREFIX/bin/hitl-run.sh                        (Termux-side Allow/Deny notification gate)
#   - proot /usr/local/bin/txr                       (run a Termux cmd from the proot, args preserved)
#   - proot /usr/local/bin/termux-*                  (one thin wrapper per termux-api capability)
#   - proot /usr/local/bin/ui                        (drive other apps via the accessibility receiver)
#   - ~/.shizuku/rish + proot /usr/local/bin/shz     (Phase 6: ADB-uid shell via Shizuku; writes gated)
#
# Sensitive commands (sms-send, camera-photo, shz writes, ui taps) route through hitl-run.sh; reads run direct.
# No cc-bridge daemon changes. The on-phone claude agent just calls these as Bash commands.
set -e

PREFIX=/data/data/com.termux/files/usr
HOME_T=/data/data/com.termux/files/home
# proot-distro rootfs path varies by version — detect it.
ROOTFS=
for cand in \
  "$PREFIX/var/lib/proot-distro/containers/ubuntu/rootfs" \
  "$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" \
  "$HOME_T/.local/share/proot-distro/installed-rootfs/ubuntu"; do
  [ -d "$cand/etc" ] && { ROOTFS="$cand"; break; }
done
[ -n "$ROOTFS" ] || { echo "FATAL: ubuntu proot rootfs not found" >&2; exit 1; }
echo "rootfs: $ROOTFS"
BIN="$ROOTFS/usr/local/bin"          # on PATH for any proot shell / the agent's Bash tool
UI_SECRET="${UI_BRIDGE_SECRET:-}"    # passed in env; gates the app's UiActionReceiver (4c)
mkdir -p "$BIN" "$ROOTFS/root/.ssh"
chmod 700 "$ROOTFS/root/.ssh"

echo "=== 1/4 proot loopback ssh key ==="
if [ ! -f "$ROOTFS/root/.ssh/id_ed25519" ]; then
  ssh-keygen -t ed25519 -N "" -f "$ROOTFS/root/.ssh/id_ed25519" -q
  echo "generated proot key"
fi
PUB="$(cat "$ROOTFS/root/.ssh/id_ed25519.pub")"
mkdir -p "$HOME_T/.ssh"; chmod 700 "$HOME_T/.ssh"
if ! grep -qF "$PUB" "$HOME_T/.ssh/authorized_keys" 2>/dev/null; then
  echo "$PUB" >> "$HOME_T/.ssh/authorized_keys"; echo "authorized proot key in Termux"
fi
chmod 600 "$HOME_T/.ssh/authorized_keys"

echo "=== 2/4 hitl-run.sh (Termux Allow/Deny gate) ==="
cat > "$PREFIX/bin/hitl-run.sh" <<'HITL'
#!/data/data/com.termux/files/usr/bin/sh
# hitl-run.sh "<label>" <cmd> [args...] — blocking Yes/No confirm; runs cmd ONLY on an explicit Yes.
# ponytail: native termux-dialog confirm, default-DENY on No/cancel/error. Needs Termux:API
# "appear on top" (SYSTEM_ALERT_WINDOW=allow) + battery-exempt so the dialog surfaces from background.
export PATH=/data/data/com.termux/files/usr/bin:/system/bin:$PATH
LABEL="$1"; shift
ANS=$(termux-dialog confirm -t "Dr. CLAW — approve?" -i "$LABEL: $*" 2>/dev/null)
case "$ANS" in
  *'"text": "yes"'*|*'"text":"yes"'*) exec "$@" ;;
esac
echo "HITL denied/cancelled: $LABEL: $*" >&2
exit 13
HITL
chmod +x "$PREFIX/bin/hitl-run.sh"

echo "=== 3/4 proot txr dispatcher ==="
cat > "$BIN/txr" <<'TXR'
#!/bin/sh
# txr <cmd> [args...] — run a command in NATIVE Termux via ssh-loopback, args preserved exactly.
# ponytail: one ssh per call; fine at human interaction rates. Add ControlPersist only if latency bites.
_q() { for a in "$@"; do printf "'%s' " "$(printf '%s' "$a" | sed "s/'/'\\\\''/g")"; done; }
exec ssh -p 8022 -o BatchMode=yes -o StrictHostKeyChecking=accept-new u0_a711@localhost \
  "export PATH=/data/data/com.termux/files/usr/bin:/system/bin; $(_q "$@")"
TXR
chmod +x "$BIN/txr"

echo "=== 4/4 capability wrappers ==="
# Ungated (benign read / feedback) — run direct.
for c in clipboard-get clipboard-set tts-speak location battery-status sensor \
         vibrate wifi-connectioninfo torch notification sms-list; do
  cat > "$BIN/termux-$c" <<WRAP
#!/bin/sh
exec txr termux-$c "\$@"
WRAP
  chmod +x "$BIN/termux-$c"
done
# Gated (sensitive write) — route through the Allow/Deny notification gate.
cat > "$BIN/termux-sms-send" <<'WRAP'
#!/bin/sh
exec txr hitl-run.sh "Send SMS" termux-sms-send "$@"
WRAP
cat > "$BIN/termux-camera-photo" <<'WRAP'
#!/bin/sh
exec txr hitl-run.sh "Take photo" termux-camera-photo "$@"
WRAP
chmod +x "$BIN/termux-sms-send" "$BIN/termux-camera-photo"

echo "=== 5/6 ui dispatcher (drive other apps via the Dr. CLAW accessibility receiver) ==="
# Secret in a 0600 file (not baked into the script). Must equal the app's BuildConfig.UI_BRIDGE_SECRET.
if [ -n "$UI_SECRET" ]; then
  printf '%s' "$UI_SECRET" > "$ROOTFS/root/.ui-bridge-secret"
  chmod 600 "$ROOTFS/root/.ui-bridge-secret"
  echo "ui-bridge secret installed"
else
  echo "WARN: no UI_BRIDGE_SECRET passed — 'ui' commands will be rejected by the app until set" >&2
fi
cat > "$BIN/ui" <<'UI'
#!/bin/sh
# ui <tap|type|scroll|read> [--id VIEWID] [--text TEXT] [--input TXT] [--forward true|false]
# Drives OTHER apps via the Dr. CLAW accessibility receiver (am broadcast). tap/type/scroll are gated.
SECRET=$(cat /root/.ui-bridge-secret 2>/dev/null)
# EXPLICIT component target — Android 8+ does not deliver implicit broadcasts to manifest receivers.
# (debug variant; for a release build drop the ".debug" from the package.)
COMP=com.scaso.drclawapp.debug/com.scaso.drclawapp.service.UiActionReceiver
OP="$1"; [ -n "$OP" ] && shift
VIEWID=""; TEXT=""; INPUT=""; FWD="true"
while [ $# -gt 0 ]; do
  case "$1" in
    --id) VIEWID="$2"; shift 2 ;;
    --text) TEXT="$2"; shift 2 ;;
    --input) INPUT="$2"; shift 2 ;;
    --forward) FWD="$2"; shift 2 ;;
    *) shift ;;
  esac
done
set -- am broadcast -n "$COMP" -a com.scaso.drclawapp.UI_ACTION --es op "$OP" --es secret "$SECRET"
[ -n "$VIEWID" ] && set -- "$@" --es viewId "$VIEWID"
[ -n "$TEXT" ]   && set -- "$@" --es text "$TEXT"
[ -n "$INPUT" ]  && set -- "$@" --es inputText "$INPUT"
[ "$OP" = scroll ] && set -- "$@" --es forward "$FWD"
case "$OP" in
  read)  txr "$@" >/dev/null 2>&1; sleep 1; txr cat /sdcard/Download/.drclaw-ui.json ;;
  tap|type|scroll) txr hitl-run.sh "UI $OP ${TEXT:-$VIEWID}" "$@" ;;
  *) echo "usage: ui <tap|type|scroll|read> [--id ID] [--text T] [--input TXT] [--forward true|false]" >&2; exit 2 ;;
esac
UI
chmod +x "$BIN/ui"

echo "=== 6/7 shizuku rish (ADB-uid shell) + shz wrapper ==="
# rish = Shizuku's terminal shell. app_process loads rish_shizuku.dex, which talks to the armed Shizuku
# service to run commands at uid 2000(shell). Extract straight from the installed Shizuku APK (no download).
# REQUIRES one-time on-device arming: Shizuku started (wireless-debugging) + Termux granted permission.
SHIZ_DIR="$HOME_T/.shizuku"
SHIZ_APK=$(pm path moe.shizuku.privileged.api 2>/dev/null | sed -n 's/^package://p' | head -1)
if [ -n "$SHIZ_APK" ] && [ -f "$SHIZ_APK" ] && command -v unzip >/dev/null 2>&1; then
  mkdir -p "$SHIZ_DIR/_x"
  unzip -o -q "$SHIZ_APK" assets/rish assets/rish_shizuku.dex -d "$SHIZ_DIR/_x"
  mv -f "$SHIZ_DIR/_x/assets/rish" "$SHIZ_DIR/rish"
  mv -f "$SHIZ_DIR/_x/assets/rish_shizuku.dex" "$SHIZ_DIR/rish_shizuku.dex"
  rm -rf "$SHIZ_DIR/_x"
  sed -i 's/"PKG"/"com.termux"/' "$SHIZ_DIR/rish"   # tell rish which app holds the Shizuku permission
  chmod 700 "$SHIZ_DIR/rish"
  chmod 400 "$SHIZ_DIR/rish_shizuku.dex"             # Android 14+: app_process refuses a writable dex
  echo "rish staged at $SHIZ_DIR (RISH_APPLICATION_ID=com.termux)"
else
  echo "WARN: Shizuku APK / unzip missing — shz will not work until Shizuku is installed + rish staged" >&2
fi
cat > "$BIN/shz" <<'SHZ'
#!/bin/sh
# shz <cmd> [args...] — run a command at ADB/shell privilege (uid 2000) on the phone via Shizuku rish.
# Chain: (proot) shz -> txr ssh-loopback -> (Termux) rish -c "<cmd>" -> exec at uid 2000(shell), u:r:shell:s0.
# Dangerous verbs are HITL-gated (termux-dialog Yes/No via hitl-run.sh); read-only commands run free.
# ponytail: gate by first-token allow/deny list — coarse but safe (shell uid, NOT root: can't brick /
#           can't read other apps' private /data). Unlisted commands run UNGATED; extend needs_hitl() as needed.
RISH=/data/data/com.termux/files/home/.shizuku/rish
_q() { for a in "$@"; do printf "'%s' " "$(printf '%s' "$a" | sed "s/'/'\\\\''/g")"; done; }
needs_hitl() {
  case "$1" in
    pm)       case "$2" in install|uninstall|clear|enable|disable*|grant|revoke|set-*) return 0;; esac ;;
    am)       case "$2" in start*|broadcast|force-stop|kill*|crash|instrument)         return 0;; esac ;;
    settings) case "$2" in put|delete|reset)                                            return 0;; esac ;;
    content)  case "$2" in insert|update|delete|call)                                   return 0;; esac ;;
    input|svc|reboot|setprop|wm|monkey|cmd|ime) return 0 ;;
  esac
  return 1
}
CMD=$(_q "$@")
if needs_hitl "$@"; then
  exec txr hitl-run.sh "shz: $*" "$RISH" -c "$CMD"
else
  exec txr "$RISH" -c "$CMD"
fi
SHZ
chmod +x "$BIN/shz"

echo "=== 7/7 DEVICE.md (agent-facing tool list) -> proot /root/DEVICE.md ==="
cat > "$ROOTFS/root/DEVICE.md" <<'DEV'
# Phone device tools (you are the Dr. CLAW agent on the user's phone)

Drive this Android phone with the commands below (already on your PATH). Sensitive ones marked
APPROVAL pop a "Dr. CLAW - approve?" dialog and BLOCK until the user taps Yes; a deny makes the command
exit non-zero — just report you were denied, don't retry.

## Device (termux-api)
- `termux-tts-speak "text"`                   speak aloud
- `termux-clipboard-get` / `termux-clipboard-set "text"`
- `termux-battery-status`                     battery JSON
- `termux-location`                           GPS JSON (can take a few seconds)
- `termux-wifi-connectioninfo`                wifi JSON
- `termux-sensor -s <name> -n 1`              one sensor reading
- `termux-vibrate` / `termux-torch on|off`
- `termux-notification --title T --content C` post a phone notification
- `termux-sms-list`                           recent SMS JSON
- `termux-sms-send -n <number> "text"`        APPROVAL  send a real SMS
- `termux-camera-photo -c 0 /sdcard/x.jpg`    APPROVAL  take a photo

## Drive other apps' UI (accessibility service must be enabled in Settings)
- `ui read`                                   dump current screen as a JSON node tree
- `ui tap --text "Send"` | `ui tap --id <viewId>`       APPROVAL  tap a control
- `ui type --id <viewId> --input "hello"`               APPROVAL  type into a field
- `ui scroll --id <viewId> --forward false`             APPROVAL  scroll
Operate another app: open it, `ui read` to find nodes (match by `text` or `viewId`), then tap/type/
scroll, and `ui read` again to confirm the screen changed.

## Privileged shell — ADB-power (Shizuku rish)
Run any command at **uid 2000 (shell)** — same power as `adb shell`. NOT root: cannot read other apps'
private `/data`. Write/control verbs are APPROVAL-gated; reads run free.
- `shz pm list packages` / `shz pm path <pkg>`              list / locate installed apps
- `shz dumpsys <service>`                                   e.g. `shz dumpsys battery`, `shz dumpsys wifi`
- `shz getprop <prop>` / `shz settings get <ns> <key>`      read system props / settings
- `shz settings put <ns> <key> <value>`         APPROVAL    change a setting
- `shz pm install <apk>` / `shz pm uninstall <pkg>`  APPROVAL  install / remove an app
- `shz am force-stop <pkg>` / `shz am start -n <comp>`  APPROVAL  stop / launch an app
- `shz input tap <x> <y>` / `shz input text "<s>"`  APPROVAL  inject taps/keys (coords; prefer `ui` for element-based)
DEV

echo "=== self-check: battery-status via the proot wrapper ==="
if proot-distro login ubuntu -- /usr/local/bin/termux-battery-status 2>/dev/null | grep -q '"percentage"'; then
  echo "SELF_CHECK_OK — proot agent can call termux-api."
else
  echo "SELF_CHECK_FAILED" >&2; exit 1
fi
