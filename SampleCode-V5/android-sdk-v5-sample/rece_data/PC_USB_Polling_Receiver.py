#!/usr/bin/env python3
"""
PC端USB轮询数据接收程序
安卓设备作为USB从设备，PC作为USB主机主动轮询数据

使用说明：
1. 连接DJI遥控器到PC（USB-C接口）
2. 在DJI遥控器上启用USB调试模式
3. 运行此Python程序
4. 程序会自动检测并连接Android设备
5. 开始轮询数据

@author USB开发
@date 2024/1/1
"""

import time
import sys
import os
import threading
from datetime import datetime

try:
    import usb.core
    import usb.util
    USB_AVAILABLE = True
except ImportError:
    USB_AVAILABLE = False
    print("⚠️ 警告: pyusb库未安装，使用模拟模式")
    print("安装命令: pip install pyusb")

class USBPollingReceiver:
    def __init__(self, polling_interval=1.0):
        """
        初始化USB轮询接收器
        
        Args:
            polling_interval: 轮询间隔（秒）
        """
        self.polling_interval = polling_interval
        self.is_running = False
        self.device = None
        self.polling_thread = None
        self.data_received = []
        self.total_bytes = 0
        self.start_time = None
        
        # 模拟模式标志
        self.simulation_mode = not USB_AVAILABLE
        
        print("🚀 PC USB轮询数据接收器")
        print("=" * 50)
        
    def find_android_device(self):
        """查找连接的Android设备"""
        if self.simulation_mode:
            print("🔧 模拟模式: 使用模拟Android设备")
            return "模拟设备"
            
        try:
            # 查找Android设备（通常VID为0x18d1）
            device = usb.core.find(idVendor=0x18d1)
            if device is not None:
                print(f"✅ 找到Android设备: VID={device.idVendor:04x}, PID={device.idProduct:04x}")
                return device
            else:
                # 尝试查找其他可能的Android设备VID
                android_vids = [0x18d1, 0x0bb4, 0x22b8, 0x04e8]  # Google, HTC, Motorola, Samsung
                for vid in android_vids:
                    device = usb.core.find(idVendor=vid)
                    if device is not None:
                        print(f"✅ 找到Android设备: VID={device.idVendor:04x}, PID={device.idProduct:04x}")
                        return device
                
                print("❌ 未找到Android设备")
                return None
                
        except Exception as e:
            print(f"❌ 查找设备失败: {e}")
            return None
    
    def connect_to_device(self):
        """连接到Android设备"""
        if self.simulation_mode:
            print("🔗 模拟连接: 已连接到模拟Android设备")
            self.device = "模拟设备"
            return True
            
        self.device = self.find_android_device()
        if self.device is None:
            return False
            
        try:
            # 尝试配置设备
            if self.device.is_kernel_driver_active(0):
                self.device.detach_kernel_driver(0)
                print("🔧 已分离内核驱动")
            
            # 设置配置
            self.device.set_configuration()
            print("✅ 设备配置成功")
            return True
            
        except Exception as e:
            print(f"❌ 设备连接失败: {e}")
            return False
    
    def send_poll_request(self):
        """发送轮询请求"""
        if self.simulation_mode:
            # 模拟数据接收
            import random
            if random.random() > 0.7:  # 30%概率有数据
                data = f"模拟数据 {datetime.now().strftime('%H:%M:%S')}".encode('utf-8')
                return data
            else:
                return None  # 模拟NAK
                
        if self.device is None:
            return None
            
        try:
            # 发送轮询请求（这里需要根据实际USB协议实现）
            # 实际实现需要根据Android设备的USB端点配置
            
            # 模拟实现：尝试读取数据
            endpoint_addr = 0x81  # 通常的IN端点地址
            data = self.device.read(endpoint_addr, 64, timeout=1000)
            
            if data and len(data) > 0:
                return bytes(data)
            else:
                return None  # 无数据
                
        except usb.core.USBError as e:
            if e.errno == 110:  # 超时，无数据
                return None
            else:
                print(f"⚠️ USB读取错误: {e}")
                return None
        except Exception as e:
            print(f"❌ 轮询请求失败: {e}")
            return None
    
    def process_received_data(self, data):
        """处理接收到的数据"""
        if data is None:
            print("⏳ 无数据，继续轮询...")
            return
            
        try:
            # 尝试解码数据
            text_data = data.decode('utf-8', errors='ignore').strip()
            timestamp = datetime.now().strftime('%H:%M:%S.%f')[:-3]
            
            self.data_received.append({
                'timestamp': timestamp,
                'data': data,
                'text': text_data,
                'size': len(data)
            })
            
            self.total_bytes += len(data)
            
            print(f"📥 [{timestamp}] 收到数据: {text_data} (大小: {len(data)} 字节)")
            
        except Exception as e:
            print(f"❌ 数据处理失败: {e}")
    
    def polling_worker(self):
        """轮询工作线程"""
        poll_count = 0
        
        while self.is_running:
            try:
                poll_count += 1
                
                # 发送轮询请求
                data = self.send_poll_request()
                
                # 处理接收到的数据
                self.process_received_data(data)
                
                # 显示统计信息（每10次轮询显示一次）
                if poll_count % 10 == 0:
                    elapsed_time = time.time() - self.start_time
                    if elapsed_time > 0:
                        rate = self.total_bytes / elapsed_time
                        print(f"📊 统计: 轮询{poll_count}次, 接收{len(self.data_received)}条数据, "
                              f"总计{self.total_bytes}字节, 速率{rate:.2f} B/s")
                
                # 等待轮询间隔
                time.sleep(self.polling_interval)
                
            except Exception as e:
                print(f"❌ 轮询错误: {e}")
                time.sleep(self.polling_interval)
    
    def start_polling(self):
        """开始轮询"""
        if not self.connect_to_device():
            print("❌ 无法连接到设备，无法开始轮询")
            return False
        
        self.is_running = True
        self.start_time = time.time()
        self.data_received.clear()
        self.total_bytes = 0
        
        print("🔍 开始轮询数据...")
        print(f"⏱️  轮询间隔: {self.polling_interval}秒")
        print("-" * 50)
        
        # 启动轮询线程
        self.polling_thread = threading.Thread(target=self.polling_worker)
        self.polling_thread.daemon = True
        self.polling_thread.start()
        
        return True
    
    def stop_polling(self):
        """停止轮询"""
        self.is_running = False
        
        if self.polling_thread and self.polling_thread.is_alive():
            self.polling_thread.join(timeout=2.0)
        
        if self.start_time:
            elapsed_time = time.time() - self.start_time
            print(f"\n🛑 轮询已停止")
            print(f"⏱️  总运行时间: {elapsed_time:.2f}秒")
            print(f"📊 总接收数据: {len(self.data_received)}条")
            print(f"💾 总字节数: {self.total_bytes}字节")
            
            if elapsed_time > 0:
                rate = self.total_bytes / elapsed_time
                print(f"🚀 平均速率: {rate:.2f} B/s")
    
    def show_statistics(self):
        """显示统计信息"""
        if not self.data_received:
            print("📊 暂无接收数据")
            return
            
        print("\n" + "=" * 50)
        print("📊 数据接收统计")
        print("=" * 50)
        
        # 显示最近几条数据
        print("\n最近接收的数据:")
        for i, item in enumerate(self.data_received[-5:], 1):
            print(f"{i}. [{item['timestamp']}] {item['text']} ({item['size']}字节)")
        
        # 总体统计
        print(f"\n总体统计:")
        print(f"• 总数据条数: {len(self.data_received)}")
        print(f"• 总字节数: {self.total_bytes}")
        
        if self.start_time and len(self.data_received) > 0:
            elapsed_time = time.time() - self.start_time
            print(f"• 运行时间: {elapsed_time:.2f}秒")
            print(f"• 平均速率: {self.total_bytes/elapsed_time:.2f} B/s")
    
    def interactive_mode(self):
        """交互式模式"""
        print("\n🎮 交互式模式")
        print("-" * 50)
        print("可用命令:")
        print("• start - 开始轮询")
        print("• stop  - 停止轮询")
        print("• stats - 显示统计")
        print("• clear - 清空数据")
        print("• quit  - 退出程序")
        print("-" * 50)
        
        while True:
            try:
                command = input("\n请输入命令: ").strip().lower()
                
                if command == 'start':
                    if not self.is_running:
                        self.start_polling()
                    else:
                        print("⚠️ 轮询已在运行中")
                        
                elif command == 'stop':
                    if self.is_running:
                        self.stop_polling()
                    else:
                        print("⚠️ 轮询未运行")
                        
                elif command == 'stats':
                    self.show_statistics()
                    
                elif command == 'clear':
                    self.data_received.clear()
                    self.total_bytes = 0
                    print("✅ 数据已清空")
                    
                elif command == 'quit':
                    if self.is_running:
                        self.stop_polling()
                    print("👋 再见！")
                    break
                    
                elif command == '':
                    continue
                    
                else:
                    print("❌ 未知命令，请输入: start, stop, stats, clear, quit")
                    
            except KeyboardInterrupt:
                print("\n\n⚠️ 检测到中断信号")
                if self.is_running:
                    self.stop_polling()
                break
            except Exception as e:
                print(f"❌ 命令执行错误: {e}")

def main():
    """主函数"""
    print("🚀 PC USB轮询数据接收器")
    print("=" * 60)
    
    # 设置轮询间隔
    polling_interval = 1.0  # 默认1秒
    
    # 检查命令行参数
    if len(sys.argv) > 1:
        try:
            polling_interval = float(sys.argv[1])
            print(f"⏱️  使用自定义轮询间隔: {polling_interval}秒")
        except ValueError:
            print("❌ 无效的轮询间隔参数，使用默认值1.0秒")
    
    # 创建接收器实例
    receiver = USBPollingReceiver(polling_interval=polling_interval)
    
    # 检查USB支持
    if receiver.simulation_mode:
        print("🔧 运行在模拟模式（pyusb未安装）")
        print("💡 安装pyusb以获得完整功能: pip install pyusb")
    else:
        print("✅ USB支持已启用")
    
    print("\n" + "=" * 60)
    
    # 进入交互式模式
    receiver.interactive_mode()

if __name__ == "__main__":
    main()
