#!/usr/bin/env python
# -*- coding: utf-8 -*-

import usb.core
import usb.util
import usb.backend
import sys
import time

VID = 0x2CA3
PID = 0x1021

def try_backend(backend_name):
    """尝试使用指定后端查找设备，返回 (dev, backend) 或 (None, None)"""
    try:
        if backend_name == 'libusb1':
            from usb.backend import libusb1
            backend = libusb1.get_backend()
        elif backend_name == 'libusb0':
            from usb.backend import libusb0
            backend = libusb0.get_backend()
        else:
            backend = None
        dev = usb.core.find(idVendor=VID, idProduct=PID, backend=backend)
        if dev is not None:
            print(f"[{backend_name}] 找到设备")
            return dev, backend
        else:
            print(f"[{backend_name}] 未找到设备")
            return None, None
    except Exception as e:
        print(f"[{backend_name}] 后端初始化失败: {e}")
        return None, None

def main():
    print("="*50)
    print("USB 通信诊断工具")
    print(f"目标设备: VID=0x{VID:04X}, PID=0x{PID:04X}")
    print("="*50)

    # 尝试不同的后端
    dev, backend = None, None
    for backend_name in ['libusb1', 'libusb0', None]:
        dev, backend = try_backend(backend_name)
        if dev is not None:
            break

    if dev is None:
        print("\n❌ 所有后端均未找到设备。请确认：")
        print("  1. 设备已连接")
        print("  2. 驱动已用 Zadig 替换为 WinUSB 或 libusb-win32")
        print("  3. 没有其他程序（如 adb）占用设备（可运行 adb kill-server）")
        sys.exit(1)

    print(f"\n✅ 设备信息:")
    print(f"  制造商: {dev.manufacturer}")
    print(f"  产品: {dev.product}")
    print(f"  序列号: {dev.serial_number}")

    # 检查内核驱动（Windows 下忽略）
    try:
        if dev.is_kernel_driver_active(0):
            print("  内核驱动活跃，尝试分离...")
            dev.detach_kernel_driver(0)
    except NotImplementedError:
        print("  内核驱动检查: 不支持 (Windows 正常)")
    except Exception as e:
        print(f"  内核驱动检查异常: {e}")

    # 设置配置
    try:
        dev.set_configuration()
        print("  配置已设置")
    except usb.core.USBError as e:
        print(f"  设置配置可能已存在: {e}")

    # 枚举接口和端点
    cfg = dev.get_active_configuration()
    intf = cfg[(0, 0)]
    print(f"\n📋 接口 {intf.bInterfaceNumber} 端点列表:")
    ep_in = None
    ep_out = None
    for ep in intf:
        # 端点类型: bmAttributes 低2位: 0=控制,1=等时,2=批量,3=中断
        ep_type = ep.bmAttributes & 0x03
        ep_dir = "IN" if (ep.bEndpointAddress & 0x80) else "OUT"
        print(f"  端点 0x{ep.bEndpointAddress:02X} | 方向: {ep_dir} | 类型: {ep_type} | 最大包长: {ep.wMaxPacketSize}")
        if ep_type == 2:  # 批量
            if ep_dir == "IN":
                ep_in = ep
            else:
                ep_out = ep

    if ep_in is None or ep_out is None:
        print("\n❌ 未找到批量 IN/OUT 端点。设备可能不支持批量传输，或当前模式不正确。")
        print("   如果这是 Android 设备，可能需要让 App 启用 AOA 模式并提供批量端点。")
        sys.exit(1)

    print(f"\n✅ 批量端点: IN=0x{ep_in.bEndpointAddress:02X}, OUT=0x{ep_out.bEndpointAddress:02X}")

    # 尝试声明接口（捕获异常）
    try:
        dev.claim_interface(0)
        print("  接口 0 声明成功")
    except NotImplementedError:
        print("⚠️ 声明接口: 不支持 (可能不影响后续读写)")
    except Exception as e:
        print(f"⚠️ 声明接口失败: {e}")

    print("\n🚀 开始通信循环 (按 Ctrl+C 退出)...")
    packet_count = 0
    try:
        while True:
            # 接收
            try:
                data = dev.read(ep_in.bEndpointAddress, ep_in.wMaxPacketSize, timeout=1000)
                if data:
                    print(f"[RECV] {len(data)} 字节: {list(data)}")
            except usb.core.USBTimeoutError:
                pass
            except usb.core.USBError as e:
                print(f"[读取错误] {e}")
                break

            # 每2秒发送一个测试包
            packet_count += 1
            if packet_count % 20 == 0:
                test_packet = bytes([0xAA, 0x01, packet_count & 0xFF])
                try:
                    bytes_sent = dev.write(ep_out.bEndpointAddress, test_packet, timeout=1000)
                    print(f"[SENT] {bytes_sent} 字节: {list(test_packet)}")
                except usb.core.USBError as e:
                    print(f"[发送错误] {e}")

            time.sleep(0.1)
    except KeyboardInterrupt:
        print("\n用户中断")
    finally:
        usb.util.dispose_resources(dev)
        print("设备已释放")

if __name__ == "__main__":
    main()