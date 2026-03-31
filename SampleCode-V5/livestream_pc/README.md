# RTSP Live Viewer (PC)

This is a simple Python UI to view the RTSP stream pushed by the DJI sample app Live tool.

## Requirements

- Python 3.8+
- `opencv-python`
- `Pillow`

Install:

```bash
pip install opencv-python Pillow
```

## Run

```bash
python rtsp_viewer.py
```

## RTSP URL examples

Try these if you are unsure about the path:

- `rtsp://user:pass@<RC_IP>:<port>/live`
- `rtsp://user:pass@<RC_IP>:<port>/`

If you use a different username/password/port in the Live tool, update them in the URL.
