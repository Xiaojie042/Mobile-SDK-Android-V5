#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PSDK 数据接收服务器
用于接收来自 Android 应用的 PSDK 数据

使用方法:
1. 确保 PC 和 Android 设备在同一网段
2. 运行此脚本: python PC端接收程序示例.py
3. 在 Android 应用中设置服务器 IP 为此 PC 的 IP 地址
4. 连接后即可接收 PSDK 数据

@author: PSDK开发
@date: 2024
"""

import socket
import json
import threading
import time
from datetime import datetime

class PSDKDataReceiver:
    def __init__(self, host='0.0.0.0', port=8888):
        self.host = host
        self.port = port
        self.server_socket = None
        self.running = False
        self.clients = []
        
    def start_server(self):
        """启动服务器"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(5)
            
            self.running = True
            print(f"🚀 PSDK 数据接收服务器已启动")
            print(f"📡 监听地址: {self.host}:{self.port}")
            print(f"⏰ 启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            print("=" * 50)
            
            while self.running:
                try:
                    client_socket, client_address = self.server_socket.accept()
                    print(f"📱 新客户端连接: {client_address[0]}:{client_address[1]}")
                    
                    # 为每个客户端创建处理线程
                    client_thread = threading.Thread(
                        target=self.handle_client,
                        args=(client_socket, client_address)
                    )
                    client_thread.daemon = True
                    client_thread.start()
                    
                    self.clients.append({
                        'socket': client_socket,
                        'address': client_address,
                        'thread': client_thread,
                        'connect_time': datetime.now()
                    })
                    
                except socket.error as e:
                    if self.running:
                        print(f"❌ 服务器错误: {e}")
                    break
                    
        except Exception as e:
            print(f"❌ 启动服务器失败: {e}")
        finally:
            self.stop_server()
    
    def handle_client(self, client_socket, client_address):
        """处理客户端连接"""
        buffer = ""
        
        try:
            while self.running:
                data = client_socket.recv(1024).decode('utf-8')
                if not data:
                    break
                
                buffer += data
                
                # 处理完整的 JSON 消息（以换行符分隔）
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    if line.strip():
                        self.process_message(line.strip(), client_address)
                        
        except socket.error as e:
            print(f"🔌 客户端 {client_address[0]}:{client_address[1]} 连接断开: {e}")
        except Exception as e:
            print(f"❌ 处理客户端数据错误: {e}")
        finally:
            try:
                client_socket.close()
            except:
                pass
            
            # 从客户端列表中移除
            self.clients = [c for c in self.clients if c['address'] != client_address]
            print(f"👋 客户端 {client_address[0]}:{client_address[1]} 已断开")
    
    def process_message(self, message, client_address):
        """处理接收到的消息"""
        try:
            # 解析 JSON 数据
            data = json.loads(message)
            
            # 获取当前时间
            receive_time = datetime.now().strftime('%H:%M:%S')
            
            # 打印接收信息
            print(f"\n📦 [{receive_time}] 收到来自 {client_address[0]} 的数据:")
            print(f"   类型: {data.get('type', 'unknown')}")
            print(f"   时间戳: {data.get('timestamp', 'N/A')}")
            print(f"   Payload索引: {data.get('payload_index', 'N/A')}")
            
            if 'device_name' in data:
                print(f"   设备名称: {data.get('device_name', 'N/A')}")
                print(f"   设备类型: {data.get('device_type', 'N/A')}")
                print(f"   序列号: {data.get('serial_number', 'N/A')}")
                print(f"   固件版本: {data.get('firmware_version', 'N/A')}")
                print(f"   连接状态: {'已连接' if data.get('is_connected') else '未连接'}")
            
            psdk_data = data.get('data', '')
            if psdk_data:
                print(f"   PSDK数据: {psdk_data}")
                
                # 如果数据看起来像十六进制，尝试解析
                if self.is_hex_string(psdk_data):
                    try:
                        hex_bytes = bytes.fromhex(psdk_data.replace(' ', ''))
                        print(f"   十六进制解析: {' '.join(f'{b:02X}' for b in hex_bytes)}")
                        print(f"   ASCII解析: {hex_bytes.decode('ascii', errors='ignore')}")
                    except:
                        pass
            
            print("-" * 50)
            
            # 可以在这里添加数据处理逻辑
            self.save_to_file(data, client_address)
            
        except json.JSONDecodeError as e:
            print(f"❌ JSON 解析错误: {e}")
            print(f"   原始数据: {message}")
        except Exception as e:
            print(f"❌ 处理消息错误: {e}")
    
    def is_hex_string(self, s):
        """检查字符串是否为十六进制格式"""
        try:
            int(s.replace(' ', ''), 16)
            return True
        except ValueError:
            return False
    
    def save_to_file(self, data, client_address):
        """保存数据到文件"""
        try:
            filename = f"psdk_data_{datetime.now().strftime('%Y%m%d')}.log"
            
            with open(filename, 'a', encoding='utf-8') as f:
                log_entry = {
                    'receive_time': datetime.now().isoformat(),
                    'client_address': f"{client_address[0]}:{client_address[1]}",
                    'data': data
                }
                f.write(json.dumps(log_entry, ensure_ascii=False) + '\n')
                
        except Exception as e:
            print(f"❌ 保存文件错误: {e}")
    
    def stop_server(self):
        """停止服务器"""
        self.running = False
        
        # 关闭所有客户端连接
        for client in self.clients:
            try:
                client['socket'].close()
            except:
                pass
        
        # 关闭服务器套接字
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
        
        print("\n🛑 服务器已停止")
    
    def show_status(self):
        """显示服务器状态"""
        while self.running:
            time.sleep(30)  # 每30秒显示一次状态
            
            if self.clients:
                print(f"\n📊 服务器状态 ({datetime.now().strftime('%H:%M:%S')}):")
                print(f"   活跃连接数: {len(self.clients)}")
                for i, client in enumerate(self.clients, 1):
                    duration = datetime.now() - client['connect_time']
                    print(f"   客户端{i}: {client['address'][0]}:{client['address'][1]} "
                          f"(连接时长: {duration.seconds}秒)")
                print("-" * 30)

def main():
    """主函数"""
    print("PSDK 数据接收服务器")
    print("=" * 50)
    
    # 获取本机 IP 地址
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        print(f"💡 建议在 Android 应用中设置服务器 IP 为: {local_ip}")
    except:
        print("💡 请手动设置 Android 应用中的服务器 IP 地址")
    
    print("=" * 50)
    
    # 创建并启动服务器
    receiver = PSDKDataReceiver()
    
    # 启动状态显示线程
    status_thread = threading.Thread(target=receiver.show_status)
    status_thread.daemon = True
    status_thread.start()
    
    try:
        receiver.start_server()
    except KeyboardInterrupt:
        print("\n⌨️  收到中断信号，正在停止服务器...")
        receiver.stop_server()

if __name__ == "__main__":
    main()