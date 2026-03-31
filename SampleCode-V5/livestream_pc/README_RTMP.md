# RTMP Live Viewer (PC)

This script is a simple Python UI that plays an RTMP stream on the PC side.

Important:
It is a pull viewer, not an RTMP server. The PC still needs an RTMP server first.
This workspace now includes a matching SRS setup in `livestream_pc/srs` and the launcher `livestream_pc/start_srs.bat`.

## Requirements

- Python 3.8+
- `opencv-python`
- `Pillow`

Install:

```bash
python -m pip install opencv-python Pillow
```

## Run

```bash
python livestream_pc/rtmp_viewer.py
```

## Recommended RTMP URL

Use the same URL on both sides:

```text
rtmp://<PC_IP>:1935/live/stream
```

Example:

```text
rtmp://192.168.3.31:1935/live/stream
```

## Workflow

1. Start SRS on the PC with `.\livestream_pc\start_srs.bat` and make sure port `1935` is listening.
2. In the DJI sample Live page, choose `RTMP`.
3. Set the push URL to `rtmp://192.168.3.31:1935/live/stream`.
4. Start pushing on the remote controller.
5. Run `rtmp_viewer.py` and open the same URL.

## If it keeps retrying

- Confirm the RTMP server is running before the DJI app starts pushing.
- Confirm Windows Firewall allows TCP `1935`.
- Confirm the stream application and stream name match exactly:
  `live/stream`
