#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
飞控数据接收程序示例
用于接收来自Android SDK的飞控数据

运行方式:
    python PC端飞控数据接收程序示例.py

默认监听端口: 9999
"""

import socket
import json
import threading
from datetime import datetime


class FlightDataReceiver:
    def __init__(self, host='0.0.0.0', port=9999):
        """
        初始化飞控数据接收器
        
        Args:
            host: 监听地址，默认0.0.0.0表示监听所有网卡
            port: 监听端口，默认9999
        """
        self.host = host
        self.port = port
        self.server_socket = None
        self.running = False
        self.client_socket = None
        self.client_address = None
        
    def start(self):
        """启动服务器"""
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(1)
            self.running = True
            
            print(f"✅ 飞控数据接收服务器已启动")
            print(f"📡 监听地址: {self.host}:{self.port}")
            print(f"⏳ 等待Android设备连接...\n")
            
            while self.running:
                try:
                    self.client_socket, self.client_address = self.server_socket.accept()
                    print(f"🔗 客户端已连接: {self.client_address}")
                    
                    # 启动接收线程
                    receive_thread = threading.Thread(target=self.receive_data)
                    receive_thread.daemon = True
                    receive_thread.start()
                    receive_thread.join()
                    
                except Exception as e:
                    if self.running:
                        print(f"❌ 接受连接时出错: {e}")
                        
        except Exception as e:
            print(f"❌ 启动服务器失败: {e}")
        finally:
            self.stop()
    
    def receive_data(self):
        """接收并处理数据"""
        buffer = ""
        
        try:
            while self.running and self.client_socket:
                data = self.client_socket.recv(4096)
                if not data:
                    print(f"🔌 客户端 {self.client_address} 已断开连接")
                    break
                
                # 将接收到的数据添加到缓冲区
                buffer += data.decode('utf-8', errors='ignore')
                
                # 处理缓冲区中的完整JSON对象
                while '{' in buffer and '}' in buffer:
                    start = buffer.find('{')
                    end = buffer.find('}', start) + 1
                    
                    if end > start:
                        json_str = buffer[start:end]
                        buffer = buffer[end:]
                        
                        try:
                            self.process_flight_data(json_str)
                        except Exception as e:
                            print(f"⚠️ 处理数据时出错: {e}")
                    else:
                        break
                        
        except Exception as e:
            print(f"❌ 接收数据时出错: {e}")
        finally:
            if self.client_socket:
                self.client_socket.close()
                self.client_socket = None
    
    def process_flight_data(self, json_str):
        """
        处理接收到的飞控数据
        
        Args:
            json_str: JSON格式的飞控数据字符串
        """
        try:
            data = json.loads(json_str)
            
            if data.get('type') == 'flight_data':
                self.display_flight_data(data)
                
                # 在这里添加您的数据处理逻辑
                # 例如: 保存到数据库、发送到其他系统、进行数据分析等
                
        except json.JSONDecodeError as e:
            print(f"⚠️ JSON解析错误: {e}")
            print(f"原始数据: {json_str[:100]}...")
    
    def display_flight_data(self, data):
        """
        显示飞控数据
        
        Args:
            data: 解析后的飞控数据字典
        """
        timestamp = data.get('timestamp', 'N/A')
        
        print(f"\n{'='*60}")
        print(f"⏰ 时间戳: {timestamp}")
        
        # 姿态数据
        if 'attitude' in data:
            attitude = data['attitude']
            print(f"📐 姿态:")
            print(f"   俯仰角(Pitch): {attitude.get('pitch', 'N/A'):.2f}°")
            print(f"   横滚角(Roll):  {attitude.get('roll', 'N/A'):.2f}°")
            print(f"   偏航角(Yaw):   {attitude.get('yaw', 'N/A'):.2f}°")
        
        # GPS位置
        if 'location' in data:
            location = data['location']
            print(f"🌍 位置:")
            print(f"   纬度:  {location.get('latitude', 'N/A'):.6f}°")
            print(f"   经度:  {location.get('longitude', 'N/A'):.6f}°")
            print(f"   海拔:  {location.get('altitude', 'N/A'):.2f} m")
        
        # 速度
        if 'velocity' in data:
            velocity = data['velocity']
            print(f"💨 速度:")
            print(f"   X轴: {velocity.get('x', 'N/A'):.2f} m/s")
            print(f"   Y轴: {velocity.get('y', 'N/A'):.2f} m/s")
            print(f"   Z轴: {velocity.get('z', 'N/A'):.2f} m/s")
        
        # 飞行模式
        if 'flight_mode' in data:
            print(f"✈️  飞行模式: {data['flight_mode']}")
        
        # 返航点
        if 'home_location' in data:
            home = data['home_location']
            print(f"🏠 返航点:")
            print(f"   纬度: {home.get('latitude', 'N/A'):.6f}°")
            print(f"   经度: {home.get('longitude', 'N/A'):.6f}°")
        
        # 电池电量
        if 'battery_percentage' in data:
            battery = data['battery_percentage']
            print(f"🔋 电池电量: {battery}%")
        
        # 卫星数量
        if 'satellite_count' in data:
            print(f"🛰️  卫星数量: {data['satellite_count']}")
        
        # 飞行状态
        if 'is_flying' in data:
            status = "飞行中" if data['is_flying'] else "未飞行"
            print(f"🚁 飞行状态: {status}")
        
        print(f"{'='*60}")
    
    def stop(self):
        """停止服务器"""
        self.running = False
        
        if self.client_socket:
            try:
                self.client_socket.close()
            except:
                pass
        
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
        
        print("\n🛑 服务器已停止")


def main():
    """主函数"""
    print("="*60)
    print("飞控数据接收程序")
    print("="*60)
    
    # 创建接收器实例
    receiver = FlightDataReceiver(host='0.0.0.0', port=9999)
    
    try:
        # 启动服务器
        receiver.start()
    except KeyboardInterrupt:
        print("\n\n⚠️ 收到中断信号，正在关闭服务器...")
        receiver.stop()


if __name__ == '__main__':
    main()
