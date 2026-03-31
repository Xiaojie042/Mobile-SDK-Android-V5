# SRS For DJI RTMP Test

This folder contains a minimal SRS setup for the DJI MSDK live stream test on Windows.

## Directory layout

Use any one of these placements for `srs.exe`:

- `livestream_pc\srs.exe`
- `livestream_pc\srs\bin\srs.exe`
- `livestream_pc\srs\objs\srs.exe`

The config file is already prepared here:

- `livestream_pc\srs\conf\srs_rtmp.conf`

## Start SRS

Run:

```powershell
.\livestream_pc\start_srs.bat
```

If SRS starts correctly, keep that terminal open.

## Check ports

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\livestream_pc\check_rtmp_server.ps1
```

Expected listening ports:

- `1935` for RTMP
- `1985` for SRS HTTP API
- `8080` for SRS HTTP server

## DJI Live configuration

In the DJI sample Live page, select `RTMP` and set:

```text
rtmp://192.168.3.31:1935/live/stream
```

Replace `192.168.3.31` if your PC IP changes.

## Python viewer

After the remote controller starts pushing, run:

```powershell
python .\livestream_pc\rtmp_viewer.py
```

Open the same RTMP URL inside the viewer:

```text
rtmp://192.168.3.31:1935/live/stream
```

## Troubleshooting

- If the viewer keeps retrying, confirm `start_srs.bat` is still running.
- If port `1935` is not listening, SRS did not start correctly.
- If the DJI side says streaming but the viewer has no image, verify the push URL matches `live/stream` exactly.
- If Windows Firewall blocks the connection, allow inbound TCP `1935`.
