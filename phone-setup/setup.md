# Phase 4 — phone-as-device setup (reproducible)

Wires the on-phone `claude` agent (running in the Termux proot) to the phone's `termux-api`
capabilities and the Dr. CLAW accessibility service. The agent calls these as ordinary Bash
commands; sensitive ones block on a **Dr. CLAW approve** dialog.

Prereqs (Phase 1, already in place): Termux + Termux:API APK + Termux:Boot (GitHub builds, sig-matched),
proot-distro `ubuntu` with `claude`, the cc-bridge daemon, sshd on `0.0.0.0:8022`.

## 1. One-time adb grants  (run from workstation — these need adb/shell, the Termux installer can't do them)
The Termux:API dialog gate (`termux-dialog confirm`) only surfaces from the background if Termux:API
can draw over other apps **and** is battery-exempt. Without this, approval popups silently never appear.

```bash
ADB="adb"   # or the full path to platform-tools/adb.exe if it is not on PATH
"$ADB" connect 198.51.100.206:<wireless-debug-port>     # port rotates per reboot
"$ADB" shell appops set com.termux.api SYSTEM_ALERT_WINDOW allow
"$ADB" shell dumpsys deviceidle whitelist +com.termux.api
# Per-capability runtime permissions Termux:API needs (grant the ones you'll use):
"$ADB" shell pm grant com.termux.api android.permission.SEND_SMS            # termux-sms-send
"$ADB" shell pm grant com.termux.api android.permission.READ_PHONE_STATE    # also required by sms-send (SIM)
"$ADB" shell pm grant com.termux.api android.permission.READ_SMS            # termux-sms-list
"$ADB" shell pm grant com.termux.api android.permission.CAMERA              # termux-camera-photo
"$ADB" shell pm grant com.termux.api android.permission.ACCESS_FINE_LOCATION # termux-location
```
Persists across reboots; only resets if Termux:API is reinstalled. (`com.termux` + `com.termux.boot`
were already battery-whitelisted by Phase 1; `com.termux.api` was missing.) Note: `termux-sms-send`
needs BOTH `SEND_SMS` and `READ_PHONE_STATE` (verified live — sms-send fails on the latter otherwise).

## 2. termux-api CLI package  (in Termux)
The Termux:API **APK** ≠ the `termux-api` **CLI** package. Phase 1 installed only the APK, so the
`termux-*` commands were absent. Install the CLI scripts:
```bash
pkg install -y termux-api
```

## 3. Install the device bridge  (from workstation, over ssh)
```bash
# Run from a FILE, not piped. `ssh ... 'sh -s' < file` lands on Android's mksh, which spools heredocs
# to /data/local (no write perm) and dies "can't create temporary file". A seekable file avoids that.
scp -P 8022 install-device-bridge.sh u0_a711@198.51.100.206:
ssh -p 8022 u0_a711@198.51.100.206 'chmod +x ~/install-device-bridge.sh && ~/install-device-bridge.sh'
```
Idempotent. Creates the proot loopback ssh key (authorized in Termux), `$PREFIX/bin/hitl-run.sh`
(the dialog gate), proot `/usr/local/bin/txr` (runs a Termux cmd from the proot, args preserved),
one `termux-*` wrapper per capability, the `ui` accessibility wrapper, and (Phase 6) stages
`~/.shizuku/rish` + the `shz` privileged-shell wrapper. Ends with a self-check (`termux-battery-status`
via the proot wrapper). NOTE: the self-check needs Termux sshd up on :8022 — if sshd has dropped it
fails spuriously (see the sshd-persistence caveat in §5).

## 4. Enable the accessibility service  (manual, on the phone — for `ui-*` "drive other apps")
Settings → Accessibility → installed services → **Dr. CLAW** → On. Required for `ui-tap/type/scroll/read`.
The `UI_BRIDGE_SECRET` (in `secrets.properties`, baked into the APK and the proot `ui-*` wrappers)
gates the exported receiver so only our wrappers can drive the UI.

## 5. Phase 6 — Shizuku ADB-power shell (`shz`)
Gives the proot agent a shell at **uid 2000 (shell)** — the same power as `adb shell` (`pm`, `am`,
`input`, `settings`, `dumpsys`, `svc`, `wm`…). NOT root: cannot read other apps' private `/data`.

**One-time arming (manual, on the phone — cannot be scripted remotely; see gotcha):**
1. Install/keep **Shizuku ≥ 13.6.0** (sig-matched GitHub build). 13.6.0 adds auto-start-without-root
   on a **trusted Wi-Fi** (Android 13+), so home reboots re-arm Shizuku on their own.
2. Enable **Developer options → Wireless debugging**.
3. Open **Shizuku → Start** (pair via wireless debugging when prompted). The service now runs as uid 2000.
4. First `shz`/`rish` call triggers a **Shizuku authorization** dialog for Termux → tap **Allow** (one-time).

**Gotcha (learned live 2026-06-25):** you **cannot** start Shizuku from a remote/Meshnet `adb shell`
one-shot — the server crashes immediately (`AndroidRuntime: Bad file descriptor` in BinderProxy) on
this Samsung/Android 16 build. Use the **on-device** wireless-debugging start above; it runs from the
phone's own loopback adb with a clean binder context and persists.

`rish` + `shz` themselves are staged by `install-device-bridge.sh` (§3) — rish is extracted straight
from the installed Shizuku APK (no download), `RISH_APPLICATION_ID=com.termux`, dex chmod 400
(Android 14+ won't load a writable dex). Verify: `proot-distro login ubuntu -- shz id` → `uid=2000(shell)`.

**sshd persistence (the durable stack — in place + verified 2026-06-25):**
- Termux:Boot script `~/.termux/boot/00-home-away.sh` runs on every boot: `termux-wake-lock` (survive
  Doze) + `sshd` + the cc-bridge tmux. This is the durable mechanism for reboots.
- `com.termux`, `com.termux.api`, `com.termux.boot` are all battery-whitelisted (`dumpsys deviceidle
  whitelist`); standby bucket set to `active`.
- **After a manual `run-as` revival** (when you bring sshd back by hand from workstation), the wake-lock is
  NOT held — also run `ssh -p 8022 … /data/data/com.termux/files/usr/bin/termux-wake-lock`, else Doze
  kills sshd again. (Verified: holding it shows `PARTIAL_WAKE_LOCK 'termux:service-wakelock'` for com.termux.)
- Manual revive recipe (wireless-adb port rotates per reboot — get it from the phone's Wireless
  debugging screen, or `adb mdns services`): `adb connect <ip:port>` then
  `MSYS_NO_PATHCONV=1 adb -s <ip:port> shell run-as com.termux /data/data/com.termux/files/usr/bin/sshd`.
- Residual (only if Samsung still kills Termux between reboots): a workstation-side watchdog that finds the
  phone via `adb mdns services` and re-runs the revive recipe. Not built — add if doze-kills recur.

**Shizuku persistence:** the Shizuku **server** (uid 2000) does NOT survive reboot/Doze on its own — if
`shz` returns `Server is not running`, Shizuku stopped. It can't be re-armed from a remote adb (the
server crashes — see the gotcha above), so re-arm on-device (**Shizuku → Start**). Enable Shizuku's
**auto-start on trusted Wi-Fi** (13.6.0, Settings) so it self-re-arms at home and you rarely touch it.

## How it works
```
proot claude  --(/usr/local/bin/termux-* | ui | shz)-->  txr  --ssh:8022 loopback-->  Termux
   shz: rish -> uid 2000(shell)          others: termux-api / am broadcast            |  (gated cmds)
                                                                          hitl-run.sh -> termux-dialog confirm (Dr. CLAW approve)
```
No cc-bridge daemon / workstation changes. Gated: `sms-send`, `camera-photo`, `ui tap/type/scroll`, and
`shz` write/control verbs (pm install/uninstall, am force-stop/start, settings put, input, svc, reboot,
wm, setprop, monkey, cmd, content write). Ungated: clipboard-get, tts-speak, location, battery-status,
sensor, vibrate, wifi-info, torch, notification, sms-list, ui read, and `shz` reads (dumpsys, getprop,
pm list, settings get, …).
