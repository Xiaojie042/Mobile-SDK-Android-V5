import ctypes.util
import os
import sys
import time

LOCAL_DEPS = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".pydeps")
if os.path.isdir(LOCAL_DEPS) and LOCAL_DEPS not in sys.path:
    sys.path.insert(0, LOCAL_DEPS)

try:
    import usb.core
    import usb.util
    from usb.backend import libusb0, libusb1
except ImportError:
    print("Missing package: pyusb")
    print("Install with: python -m pip install pyusb")
    sys.exit(1)

VID = 0x2CA3
PID = 0x1021
TARGET_INTERFACE = 0  # Set to None to auto-pick first BULK IN/OUT interface.
SEND_INTERVAL_SEC = 1.0
READ_TIMEOUT_MS = 100
WRITE_TIMEOUT_MS = 1000


def _libusb_find_library(name):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.environ.get("LIBUSB_DLL", ""),
        os.path.join(script_dir, "libusb-1.0.dll"),
        os.path.join(script_dir, "libusb0.dll"),
        "libusb-1.0.dll",
        "libusb0.dll",
    ]
    for path in candidates:
        if path and os.path.exists(path):
            return path
    return ctypes.util.find_library(name)


def find_device(vid, pid):
    backend_candidates = [
        ("libusb1", libusb1.get_backend(find_library=_libusb_find_library)),
        ("libusb0", libusb0.get_backend()),
        ("default", None),
    ]

    for name, backend in backend_candidates:
        if name != "default" and backend is None:
            print(f"[{name}] backend unavailable")
            continue
        try:
            dev = usb.core.find(idVendor=vid, idProduct=pid, backend=backend)
            if dev is not None:
                print(f"[{name}] device found")
                return dev, name
            print(f"[{name}] device not found")
        except Exception as exc:
            print(f"[{name}] backend error: {exc}")

    return None, None


def select_interface_and_endpoints(dev):
    try:
        dev.set_configuration()
    except usb.core.USBError as exc:
        print(f"set_configuration skipped: {exc}")

    cfg = dev.get_active_configuration()
    best = None

    interfaces = list(cfg)
    if TARGET_INTERFACE is not None:
        interfaces = [i for i in interfaces if i.bInterfaceNumber == TARGET_INTERFACE]
        if not interfaces:
            print(f"Configured TARGET_INTERFACE={TARGET_INTERFACE} not found.")
            interfaces = list(cfg)

    for intf in interfaces:
        ep_in = None
        ep_out = None

        for ep in intf:
            ep_addr = ep.bEndpointAddress
            ep_type = usb.util.endpoint_type(ep.bmAttributes)
            ep_dir = usb.util.endpoint_direction(ep_addr)

            type_name = {
                usb.util.ENDPOINT_TYPE_CTRL: "CONTROL",
                usb.util.ENDPOINT_TYPE_ISO: "ISO",
                usb.util.ENDPOINT_TYPE_BULK: "BULK",
                usb.util.ENDPOINT_TYPE_INTR: "INTERRUPT",
            }.get(ep_type, f"UNKNOWN({ep_type})")
            dir_name = "IN" if ep_dir == usb.util.ENDPOINT_IN else "OUT"
            print(
                f"Interface {intf.bInterfaceNumber} alt {intf.bAlternateSetting}: "
                f"EP 0x{ep_addr:02X} {dir_name} {type_name} max={ep.wMaxPacketSize}"
            )

            if ep_type == usb.util.ENDPOINT_TYPE_BULK:
                if ep_dir == usb.util.ENDPOINT_IN and ep_in is None:
                    ep_in = ep
                elif ep_dir == usb.util.ENDPOINT_OUT and ep_out is None:
                    ep_out = ep

        if ep_in is not None and ep_out is not None:
            best = (intf, ep_in, ep_out)
            break

    return best


def main():
    print(f"Target device: VID=0x{VID:04X} PID=0x{PID:04X}")
    dev, backend_name = find_device(VID, PID)
    if dev is None:
        print("Device not found with pyusb backends.")
        print("Hint: install libusb-1.0 and place libusb-1.0.dll next to this script.")
        sys.exit(1)

    print(f"Using backend: {backend_name}")
    print(f"Found device: bus={dev.bus} address={dev.address}")
    if dev.manufacturer or dev.product:
        print(f"Product: {dev.manufacturer} {dev.product}")

    selected = select_interface_and_endpoints(dev)
    if selected is None:
        print("No BULK IN/OUT endpoint pair found in active configuration.")
        print("Please verify the interface mode exposed by the firmware.")
        usb.util.dispose_resources(dev)
        sys.exit(1)

    intf, ep_in, ep_out = selected
    print(
        f"Using interface {intf.bInterfaceNumber}, "
        f"EP IN=0x{ep_in.bEndpointAddress:02X}, OUT=0x{ep_out.bEndpointAddress:02X}"
    )

    try:
        usb.util.claim_interface(dev, intf.bInterfaceNumber)
    except Exception as exc:
        print(f"claim_interface warning: {exc}")

    print("Start communication, Ctrl+C to stop.")
    last_send_ts = 0.0
    counter = 0
    try:
        while True:
            try:
                data = dev.read(
                    ep_in.bEndpointAddress,
                    ep_in.wMaxPacketSize,
                    timeout=READ_TIMEOUT_MS,
                )
                if data:
                    print(f"[RECV] {len(data)} bytes: {list(data)}")
            except NotImplementedError as exc:
                print(f"[RECV][NotSupported] {exc}")
                print("Driver binding issue is likely present on interface MI_00.")
                print("Use Zadig to switch MI_00 to WinUSB/libusbK, then retry.")
                break
            except usb.core.USBTimeoutError:
                pass
            except usb.core.USBError as exc:
                print(f"[RECV][USBError] {exc}")
                break

            now = time.monotonic()
            if now - last_send_ts >= SEND_INTERVAL_SEC:
                counter = (counter + 1) & 0xFF
                payload = bytes([0xAA, 0x01, counter])
                try:
                    written = dev.write(
                        ep_out.bEndpointAddress,
                        payload,
                        timeout=WRITE_TIMEOUT_MS,
                    )
                    print(f"[SENT] {written} bytes: {list(payload)}")
                except NotImplementedError as exc:
                    print(f"[SENT][NotSupported] {exc}")
                    print("Driver binding issue is likely present on interface MI_00.")
                    print("Use Zadig to switch MI_00 to WinUSB/libusbK, then retry.")
                    break
                except usb.core.USBError as exc:
                    print(f"[SENT][USBError] {exc}")
                last_send_ts = now
    except KeyboardInterrupt:
        print("Interrupted by user.")
    finally:
        usb.util.dispose_resources(dev)
        print("Device released.")


if __name__ == "__main__":
    main()
