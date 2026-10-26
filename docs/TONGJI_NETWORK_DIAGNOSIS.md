# Tongji API network diagnosis

Date: 26 October 2026.

## Confirmed cause

Two independent routing problems prevented the Java client from reaching the
campus API despite a connected MotionPro tunnel:

1. The Windows web proxy was `127.0.0.1:57777`, owned by `365vpn.exe`.
   The opt-in Java process used this proxy and received a CONNECT failure (502).
2. The ordinary DNS lookup returned public addresses `202.120.188.251` and
   `202.120.188.252`. Direct connections to both addresses timed out through
   the connected campus VPN. A query to the VPN DNS server at `127.0.0.1`
   instead returned `10.67.0.251` and `10.67.0.252`.

The WLAN IPv4 interface metric was 5 and the campus VPN metric was 25.
This explains the preference for the WLAN DNS configuration. Changing DNS
priority must be verified using a fresh ordinary lookup, rather than inferred
from an enabled VPN adapter or the default IP route.

## Controlled verification

Requests explicitly resolving the original HTTPS hostname to either campus
address completed certificate-verified TLS. No certificate checks were disabled.
Without a credential, `/v1/models` returned `API密钥不能为空`, rather than the
earlier unsupported-network error.

Using the existing protected credential through the campus address:

- `/v1/models` returned a model list containing `DeepSeek-V4.1-Flash`.
- `/v1/chat/completions` using that model returned `OK.` with finish reason
  `stop` for a minimal connectivity prompt.

This verifies the key, model identifier and campus API path. It does not by
itself verify course-grounded answers, the normal Java DNS path, or the IDE UI.
The credential was passed through standard input to curl configuration, not
printed or placed in process arguments. The API hostname and TLS verification
were preserved.

## Reversible repair

`scripts/repair-tongji-network.ps1` defaults to a read-only preview. With `-Apply`,
it requires administrator permission, backs up the connected MotionPro IPv4
interface metric and the current user's proxy exceptions, gives that interface
metric 1, and adds an exact proxy exception for `agi.tongji.edu.cn`.

The script clears the DNS cache and checks that an ordinary lookup returns the
same addresses as the campus resolver. If that check fails, it restores the
saved settings. It does not change DNS server addresses, API URLs, TLS settings,
firewall rules, or the 365VPN connection.

Run an elevated PowerShell:

```powershell
.\scripts\repair-tongji-network.ps1 -Apply
```

To restore the saved settings from an elevated PowerShell:

```powershell
.\scripts\repair-tongji-network.ps1 -Restore
```

Backups and repair status are written to the private local profile directory
`%LOCALAPPDATA%\CST3492Assistant`, outside the repository.

## Verified result after repair

The administrator repair completed at 01:59:37 local time. Ordinary DNS then
returned the two campus addresses. The unchanged Java runner was repeated using
`scripts/run-tongji.ps1 -Mode Live`; no per-request IP override was used in this
run. It completed in 26 seconds with `BUILD SUCCESSFUL` and `liveStatus COMPLETE`.
All five real-model scenarios returned nonempty answers. Four had actual course
sources, and the deliberately unmatched query had no citations and an explicit
general-knowledge disclaimer. See the linked real-course acceptance report for
answer examples and factual-quality limits.
