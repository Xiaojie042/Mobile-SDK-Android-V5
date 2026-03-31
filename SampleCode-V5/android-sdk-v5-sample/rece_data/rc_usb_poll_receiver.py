#!/usr/bin/env python3
"""
Poll data from the Android-side USB polling bridge.

Example:
    python rc_usb_poll_receiver.py --host 192.168.42.129 --port 18080
"""

from __future__ import annotations

import argparse
import base64
import json
import socket
import sys
import time
from pathlib import Path
from typing import Any
from urllib import error, parse, request


def build_url(host: str, port: int, path: str, **query: Any) -> str:
    encoded_query = parse.urlencode(query)
    base = f"http://{host}:{port}{path}"
    return f"{base}?{encoded_query}" if encoded_query else base


def http_get_json(url: str, timeout: float, opener: request.OpenerDirector) -> dict[str, Any]:
    req = request.Request(url, method="GET")
    with opener.open(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_status(host: str, port: int, timeout: float, opener: request.OpenerDirector) -> dict[str, Any]:
    return http_get_json(build_url(host, port, "/status"), timeout, opener)


def fetch_packet(
    host: str,
    port: int,
    timeout_s: float,
    timeout_ms: int,
    opener: request.OpenerDirector,
) -> dict[str, Any]:
    return http_get_json(
        build_url(host, port, "/poll", timeoutMs=timeout_ms),
        timeout_s,
        opener,
    )


def packet_to_bytes(packet: dict[str, Any]) -> bytes:
    payload_base64 = packet.get("payloadBase64", "")
    if not payload_base64:
        return b""
    return base64.b64decode(payload_base64)


def save_packet(packet: dict[str, Any], output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    packet_id = packet.get("id", "unknown")
    source = str(packet.get("source", "UNKNOWN")).lower()
    created = str(packet.get("createdAtMillis", int(time.time() * 1000)))
    file_path = output_dir / f"{created}_{packet_id}_{source}.bin"
    file_path.write_bytes(packet_to_bytes(packet))
    return file_path


def print_status(status: dict[str, Any]) -> None:
    print("Receiver connected to bridge")
    print(f"  pollingServerRunning: {status.get('pollingServerRunning')}")
    print(f"  queueSize: {status.get('queueSize')}")
    print(f"  pollingPort: {status.get('pollingPort')}")
    print(f"  usbAccessoryConnected: {status.get('usbAccessoryConnected')}")
    print(f"  ipv4Addresses: {status.get('ipv4Addresses')}")
    print(f"  message: {status.get('message')}")


def print_packet(packet: dict[str, Any], saved_path: Path | None) -> None:
    text = packet.get("payloadText", "")
    text_preview = text if len(text) <= 200 else text[:200] + "..."
    print(
        "[packet] "
        f"id={packet.get('id')} "
        f"source={packet.get('source')} "
        f"bytes={packet.get('byteLength')} "
        f"time={packet.get('createdAtText')}"
    )
    print(f"  text: {text_preview}")
    if saved_path is not None:
        print(f"  saved: {saved_path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Poll buffered data from the RC bridge.")
    parser.add_argument("--host", required=True, help="RC IP address exposed to the PC over USB network sharing.")
    parser.add_argument("--port", type=int, default=18080, help="Polling server port. Default: 18080")
    parser.add_argument("--timeout-ms", type=int, default=1000, help="Long-poll timeout used by /poll.")
    parser.add_argument("--request-timeout", type=float, default=5.0, help="HTTP request timeout in seconds.")
    parser.add_argument("--sleep-ms", type=int, default=100, help="Sleep after an empty poll.")
    parser.add_argument("--save-dir", default="", help="Optional directory to save each received packet as a .bin file.")
    parser.add_argument("--print-empty", action="store_true", help="Print queue state when no data is returned.")
    return parser.parse_args()


def create_http_opener() -> request.OpenerDirector:
    return request.build_opener(request.ProxyHandler({}))


def tcp_probe(host: str, port: int, timeout: float) -> None:
    with socket.create_connection((host, port), timeout=timeout):
        return


def main() -> int:
    args = parse_args()
    save_dir = Path(args.save_dir) if args.save_dir else None
    opener = create_http_opener()

    status_url = build_url(args.host, args.port, "/status")
    print(f"Target status URL: {status_url}")

    try:
        tcp_probe(args.host, args.port, args.request_timeout)
        print("TCP probe succeeded.")
    except Exception as exc:  # noqa: BLE001
        print(f"TCP probe failed: {exc}", file=sys.stderr)
        print("Check the RC USB tether IP, cable, and whether the USB page is still open.", file=sys.stderr)
        return 1

    try:
        status = fetch_status(args.host, args.port, args.request_timeout, opener)
        print_status(status)
    except error.URLError as exc:
        print(f"Failed to query /status: {exc}", file=sys.stderr)
        print("If TCP probe succeeded but /status still fails, verify the Android app is still on the USB page.", file=sys.stderr)
        return 1
    except Exception as exc:  # noqa: BLE001
        print(f"Unexpected error while querying /status: {exc}", file=sys.stderr)
        return 1

    print("Start polling. Press Ctrl+C to stop.")

    while True:
        try:
            result = fetch_packet(args.host, args.port, args.request_timeout, args.timeout_ms, opener)
            if result.get("hasData"):
                packet = result.get("packet") or {}
                saved_path = save_packet(packet, save_dir) if save_dir is not None else None
                print_packet(packet, saved_path)
            else:
                if args.print_empty:
                    print(f"[empty] queueSize={result.get('queueSize')}")
                time.sleep(max(args.sleep_ms, 0) / 1000.0)
        except KeyboardInterrupt:
            print("\nStopped by user.")
            return 0
        except error.URLError as exc:
            print(f"[warn] poll failed: {exc}", file=sys.stderr)
            time.sleep(1.0)
        except Exception as exc:  # noqa: BLE001
            print(f"[warn] unexpected poll error: {exc}", file=sys.stderr)
            time.sleep(1.0)


if __name__ == "__main__":
    raise SystemExit(main())
