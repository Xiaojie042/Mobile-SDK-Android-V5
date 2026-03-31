#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DJI USB数据接收器 - PC端Python脚本
用于接收DJI遥控器通过网络转发的USB数据

使用方法:
1. 确保PC和DJI遥控器在同一网络下
2. 在DJI遥控器上启动USB数据发送应用
3. 选择"网络转发"协议
4. 启动网络服务器
5. 运行此脚本: python pc_usb_data_receiver.py

作者: USB开发
日期: 2024/1/1
"""

import socket
import threading
import time
import json
from datetime import datetime
import argparse
import sys

def get_local_ip():
    """获取本地IP地址"""
    try:
        # 方法1: 通过连接外部服务器获取本地IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        
        # 验证IP是否在局域网范围内
        if ip.startswith("192.168.") or ip.startswith("10.") or ip.startswith("172."):
            return ip
        else:
            # 方法2: 使用主机名获取IP
            hostname = socket.gethostname()
            local_ips = socket.gethostbyname_ex(hostname)[2]
            for ip_addr in local_ips:
                if ip_addr.startswith("192.168.") and ip_addr != "127.0.0.1":
                    return ip_addr
            
            # 方法3: 手动指定或使用回环地址
            return "127.0.0.1"
    except:
        return "127.0.0.1"

class USBDataReceiver:
    def __init__(self, host='0.0.0.0', port=8080, save_to_file=False):
        self.host = host
        self.port = port
        self.save_to_file = save_to_file
        self.running = False
        self.socket = None
        self.client_socket = None
        
        # 数据统计
        self.total_bytes = 0
        self.total_packets = 0
        self.start_time = None
        
        # 数据文件
        if save_to_file:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            self.data_file = f"usb_data_{timestamp}.log"
        else:
            self.data_file = None
    
    def start_server(self):
        """启动数据接收服务器"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.bind((self.host, self.port))
            self.socket.listen(1)
            self.running = True
            self.start_time = time.time()
            
            print(f"🚀 USB数据接收器已启动")
            print(f"📡 监听地址: {get_local_ip()}:{self.port}")
            print(f"⏰ 启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            print("-" * 50)
            
            if self.save_to_file:
                print(f"💾 数据将保存到: {self.data_file}")
            
            # 启动统计显示线程
            stats_thread = threading.Thread(target=self._show_stats, daemon=True)
            stats_thread.start()
            
            # 接受连接
            self._accept_connections()
            
        except Exception as e:
            print(f"❌ 启动服务器失败: {e}")
            self.stop_server()
    
    def _accept_connections(self):
        """接受客户端连接"""
        print("⏳ 等待DJI遥控器连接...")
        while self.running:
            try:
                self.client_socket, client_address = self.socket.accept()
                print(f"✅ DJI遥控器已连接: {client_address[0]}:{client_address[1]}")
                
                # 启动数据接收线程
                receive_thread = threading.Thread(
                    target=self._receive_data, 
                    args=(self.client_socket, client_address),
                    daemon=True
                )
                receive_thread.start()
                
                # 等待当前客户端断开后再接受新连接
                receive_thread.join()
                print("⏳ 等待新的DJI遥控器连接...")
                
            except Exception as e:
                if self.running:
                    print(f"❌ 连接错误: {e}")
    
    def _receive_data(self, client_socket, client_address):
        """接收数据"""
        buffer = b''
        
        try:
            while self.running:
                data = client_socket.recv(4096)
                if not data:
                    break
                
                buffer += data
                
                # 处理完整的数据包（以换行符分隔）
                while b'\n' in buffer:
                    line, buffer = buffer.split(b'\n', 1)
                    self._process_data_packet(line, client_address)
                
        except Exception as e:
            print(f"❌ 数据接收错误: {e}")
        finally:
            print(f"🔌 DJI遥控器断开连接: {client_address[0]}")
            client_socket.close()
            self.client_socket = None
    
    def _process_data_packet(self, data, client_address):
        """处理数据包"""
        try:
            # 更新统计
            self.total_bytes += len(data)
            self.total_packets += 1
            
            # 显示数据信息
            timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
            data_hex = data.hex().upper()
            data_str = data.decode('utf-8', errors='ignore')
            
            print(f"[{timestamp}] 📦 数据包 #{self.total_packets}")
            print(f"   📊 大小: {len(data)} 字节")
            print(f"   🔢 十六进制: {data_hex[:64]}{'...' if len(data_hex) > 64 else ''}")
            print(f"   📝 文本: {data_str[:100]}{'...' if len(data_str) > 100 else ''}")
            
            # 保存到文件
            if self.save_to_file:
                self._save_to_file(data, timestamp)
                
        except Exception as e:
            print(f"❌ 数据处理错误: {e}")
    
    def _save_to_file(self, data, timestamp):
        """保存数据到文件"""
        try:
            with open(self.data_file, 'a', encoding='utf-8') as f:
                log_entry = {
                    'timestamp': timestamp,
                    'size': len(data),
                    'hex': data.hex().upper(),
                    'text': data.decode('utf-8', errors='ignore'),
                    'packet_number': self.total_packets
                }
                f.write(json.dumps(log_entry, ensure_ascii=False) + '\n')
        except Exception as e:
            print(f"❌ 保存文件错误: {e}")
    
    def _show_stats(self):
        """显示统计信息"""
        while self.running:
            time.sleep(5)  # 每5秒更新一次统计
            
            if self.start_time:
                elapsed_time = time.time() - self.start_time
                if elapsed_time > 0:
                    bytes_per_second = self.total_bytes / elapsed_time
                    packets_per_second = self.total_packets / elapsed_time
                    
                    print("-" * 50)
                    print("📈 统计信息:")
                    print(f"   ⏱️  运行时间: {elapsed_time:.1f} 秒")
                    print(f"   📦 数据包总数: {self.total_packets}")
                    print(f"   📊 总字节数: {self.total_bytes}")
                    print(f"   🚀 数据包速率: {packets_per_second:.1f} 包/秒")
                    print(f"   💨 字节速率: {bytes_per_second:.1f} 字节/秒")
                    print("-" * 50)
    
    def stop_server(self):
        """停止服务器"""
        self.running = False
        
        if self.client_socket:
            self.client_socket.close()
        
        if self.socket:
            self.socket.close()
        
        print("\n🛑 USB数据接收器已停止")
        
        # 显示最终统计
        if self.start_time:
            elapsed_time = time.time() - self.start_time
            print(f"⏱️  总运行时间: {elapsed_time:.1f} 秒")
            print(f"📦 接收数据包: {self.total_packets}")
            print(f"📊 接收字节数: {self.total_bytes}")

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='DJI USB数据接收器 - PC端')
    parser.add_argument('--host', default='0.0.0.0', help='监听地址 (默认: 0.0.0.0)')
    parser.add_argument('--port', type=int, default=8888, help='监听端口 (默认: 8888)')
    parser.add_argument('--save', action='store_true', help='保存数据到文件')
    
    args = parser.parse_args()
    
    print("🎯 DJI USB数据接收器 - PC端")
    print("=" * 50)
    
    receiver = USBDataReceiver(
        host=args.host,
        port=args.port,
        save_to_file=args.save
    )
    
    try:
        # 启动服务器
        receiver.start_server()
        
        # 等待用户输入退出
        input("\n按回车键停止接收...\n")
        
    except KeyboardInterrupt:
        print("\n\n⏹️  用户中断")
    except Exception as e:
        print(f"\n❌ 错误: {e}")
    finally:
        receiver.stop_server()

if __name__ == "__main__":
    main()