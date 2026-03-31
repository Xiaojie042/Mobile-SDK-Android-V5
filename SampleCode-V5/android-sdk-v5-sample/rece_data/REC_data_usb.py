#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DJI遥控器USB数据接收器
用于接收DJI遥控器通过USB Accessory模式发送的数据

作者: USB数据接收开发
日期: 2024年1月1日
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import threading
import time
import sys
import os
from datetime import datetime

# 检查是否安装了pyserial
try:
    import serial
    HAS_SERIAL = True
except ImportError:
    HAS_SERIAL = False
    print("警告: 未安装pyserial库，将使用模拟模式进行测试")
    print("要安装pyserial: pip install pyserial")

class USBDataReceiver:
    def __init__(self, root):
        self.root = root
        self.root.title("DJI遥控器USB数据接收器")
        self.root.geometry("800x600")
        self.root.configure(bg='#f0f0f0')
        
        # 接收状态
        self.receiving = False
        self.serial_port = None
        self.receive_thread = None
        
        # 数据统计
        self.total_packets = 0
        self.total_bytes = 0
        self.start_time = None
        
        self.setup_ui()
        self.auto_detect_port()
        
    def setup_ui(self):
        """设置用户界面"""
        
        # 主框架
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # 配置网格权重
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        main_frame.columnconfigure(1, weight=1)
        main_frame.rowconfigure(3, weight=1)
        
        # 标题
        title_label = ttk.Label(main_frame, text="DJI遥控器USB数据接收器", 
                               font=('Arial', 16, 'bold'))
        title_label.grid(row=0, column=0, columnspan=3, pady=(0, 20))
        
        # 连接控制区域
        control_frame = ttk.LabelFrame(main_frame, text="连接控制", padding="10")
        control_frame.grid(row=1, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(0, 10))
        control_frame.columnconfigure(1, weight=1)
        
        # 串口选择
        ttk.Label(control_frame, text="串口:").grid(row=0, column=0, sticky=tk.W, padx=(0, 5))
        self.port_var = tk.StringVar()
        self.port_combo = ttk.Combobox(control_frame, textvariable=self.port_var, width=15)
        self.port_combo.grid(row=0, column=1, sticky=(tk.W, tk.E), padx=(0, 10))
        
        # 刷新按钮
        refresh_btn = ttk.Button(control_frame, text="刷新端口", command=self.refresh_ports)
        refresh_btn.grid(row=0, column=2, padx=(0, 10))
        
        # 连接/断开按钮
        self.connect_btn = ttk.Button(control_frame, text="开始接收", command=self.toggle_receive)
        self.connect_btn.grid(row=0, column=3)
        
        # 状态显示
        self.status_var = tk.StringVar(value="状态: 未连接")
        status_label = ttk.Label(control_frame, textvariable=self.status_var)
        status_label.grid(row=1, column=0, columnspan=4, sticky=tk.W, pady=(10, 0))
        
        # 数据统计区域
        stats_frame = ttk.LabelFrame(main_frame, text="数据统计", padding="10")
        stats_frame.grid(row=2, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(0, 10))
        
        # 统计信息
        self.packets_var = tk.StringVar(value="数据包: 0")
        self.bytes_var = tk.StringVar(value="总字节: 0")
        self.rate_var = tk.StringVar(value="接收速率: 0 B/s")
        self.duration_var = tk.StringVar(value="运行时间: 00:00:00")
        
        ttk.Label(stats_frame, textvariable=self.packets_var).grid(row=0, column=0, sticky=tk.W, padx=(0, 20))
        ttk.Label(stats_frame, textvariable=self.bytes_var).grid(row=0, column=1, sticky=tk.W, padx=(0, 20))
        ttk.Label(stats_frame, textvariable=self.rate_var).grid(row=0, column=2, sticky=tk.W, padx=(0, 20))
        ttk.Label(stats_frame, textvariable=self.duration_var).grid(row=0, column=3, sticky=tk.W)
        
        # 数据接收区域
        data_frame = ttk.LabelFrame(main_frame, text="接收数据", padding="10")
        data_frame.grid(row=3, column=0, columnspan=3, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(0, 10))
        data_frame.columnconfigure(0, weight=1)
        data_frame.rowconfigure(0, weight=1)
        
        # 数据文本框
        self.data_text = scrolledtext.ScrolledText(data_frame, width=80, height=20, 
                                                  font=('Consolas', 10))
        self.data_text.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # 控制按钮区域
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=4, column=0, columnspan=3, pady=(10, 0))
        
        # 清空按钮
        clear_btn = ttk.Button(button_frame, text="清空数据", command=self.clear_data)
        clear_btn.grid(row=0, column=0, padx=(0, 10))
        
        # 保存按钮
        save_btn = ttk.Button(button_frame, text="保存数据", command=self.save_data)
        save_btn.grid(row=0, column=1, padx=(0, 10))
        
        # 关于按钮
        about_btn = ttk.Button(button_frame, text="关于", command=self.show_about)
        about_btn.grid(row=0, column=2)
        
        # 如果没有安装pyserial，显示警告
        if not HAS_SERIAL:
            warning_label = ttk.Label(main_frame, text="⚠️ 警告: 未安装pyserial库，使用模拟模式", 
                                    foreground='red', font=('Arial', 10, 'bold'))
            warning_label.grid(row=5, column=0, columnspan=3, pady=(10, 0))
        
    def auto_detect_port(self):
        """自动检测可用的串口"""
        ports = []
        
        if HAS_SERIAL:
            # 检测常见串口
            common_ports = ['COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9']
            
            for port in common_ports:
                try:
                    s = serial.Serial(port)
                    s.close()
                    ports.append(port)
                except (serial.SerialException, OSError):
                    pass
            
            # 在Linux/macOS上检测USB设备
            if sys.platform.startswith('linux') or sys.platform == 'darwin':
                for i in range(10):
                    port = f'/dev/ttyUSB{i}'
                    if os.path.exists(port):
                        ports.append(port)
        else:
            # 模拟模式：显示模拟端口
            ports = ['COM3 (模拟)', 'COM4 (模拟)', 'COM5 (模拟)']
        
        self.port_combo['values'] = ports
        if ports:
            self.port_var.set(ports[0])
        
    def refresh_ports(self):
        """刷新可用串口列表"""
        self.auto_detect_port()
        if HAS_SERIAL:
            messagebox.showinfo("刷新完成", f"找到 {len(self.port_combo['values'])} 个可用串口")
        else:
            messagebox.showinfo("刷新完成", "模拟模式：显示模拟串口")
            
    def toggle_receive(self):
        """切换接收状态"""
        if not self.receiving:
            self.start_receive()
        else:
            self.stop_receive()
            
    def start_receive(self):
        """开始接收数据"""
        port = self.port_var.get()
        if not port:
            messagebox.showerror("错误", "请选择串口")
            return
            
        try:
            if HAS_SERIAL:
                # 真实模式：打开串口
                self.serial_port = serial.Serial(
                    port=port,
                    baudrate=115200,  # DJI设备常用波特率
                    bytesize=serial.EIGHTBITS,
                    parity=serial.PARITY_NONE,
                    stopbits=serial.STOPBITS_ONE,
                    timeout=1  # 非阻塞读取
                )
            else:
                # 模拟模式：不实际打开串口
                self.serial_port = None
                self.log_message("🔧 模拟模式：不实际打开串口")
            
            self.receiving = True
            self.connect_btn.config(text="停止接收")
            
            if HAS_SERIAL:
                self.status_var.set(f"状态: 正在接收数据 - {port}")
            else:
                self.status_var.set(f"状态: 模拟接收数据 - {port}")
            
            # 重置统计
            self.total_packets = 0
            self.total_bytes = 0
            self.start_time = datetime.now()
            self.update_stats()
            
            # 启动接收线程
            self.receive_thread = threading.Thread(target=self.receive_data, daemon=True)
            self.receive_thread.start()
            
            # 启动统计更新线程
            self.stats_thread = threading.Thread(target=self.update_stats_loop, daemon=True)
            self.stats_thread.start()
            
            self.log_message("🚀 USB数据接收器已启动")
            if HAS_SERIAL:
                self.log_message(f"📡 正在监听串口: {port}")
            else:
                self.log_message(f"📡 模拟监听串口: {port}")
            self.log_message("⏳ 等待DJI遥控器发送数据...")
            
            # 如果是模拟模式，启动模拟数据生成
            if not HAS_SERIAL:
                self.simulate_data()
            
        except Exception as e:
            messagebox.showerror("连接错误", f"无法打开串口 {port}: {str(e)}")
            
    def stop_receive(self):
        """停止接收数据"""
        self.receiving = False
        
        if self.serial_port:
            self.serial_port.close()
            self.serial_port = None
            
        self.connect_btn.config(text="开始接收")
        self.status_var.set("状态: 已停止接收")
        
        self.log_message("🛑 USB数据接收器已停止")
        
    def receive_data(self):
        """接收数据的主循环"""
        buffer = b''
        
        while self.receiving:
            try:
                if HAS_SERIAL and self.serial_port and self.serial_port.in_waiting > 0:
                    # 真实模式：读取串口数据
                    data = self.serial_port.read(self.serial_port.in_waiting)
                    buffer += data
                    
                    # 处理完整的数据包（假设以换行符分隔）
                    while b'\n' in buffer:
                        line, buffer = buffer.split(b'\n', 1)
                        if line:  # 忽略空行
                            self.process_packet(line)
                
                time.sleep(0.01)  # 短暂休眠，避免CPU占用过高
                
            except Exception as e:
                if self.receiving:  # 只有在接收状态时才显示错误
                    self.log_message(f"❌ 接收错误: {str(e)}")
                break
                
    def simulate_data(self):
        """模拟数据生成（用于测试）"""
        def generate_simulated_data():
            test_messages = [
                b"Hello DJI USB Test\n",
                b"USB Data Transmission Test\n", 
                b"DJI Remote Controller Data\n",
                b"Test Message from Android App\n",
                b"USB Accessory Mode Working\n"
            ]
            
            count = 0
            while self.receiving and not HAS_SERIAL:
                # 每3秒发送一条模拟数据
                time.sleep(3)
                if count < len(test_messages):
                    data = test_messages[count]
                    self.process_packet(data)
                    count += 1
                else:
                    # 循环发送
                    count = 0
        
        # 启动模拟数据线程
        sim_thread = threading.Thread(target=generate_simulated_data, daemon=True)
        sim_thread.start()
        
    def process_packet(self, data):
        """处理接收到的数据包"""
        self.total_packets += 1
        self.total_bytes += len(data)
        
        # 获取当前时间
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        
        # 尝试解码为文本
        try:
            text_data = data.decode('utf-8', errors='replace').strip()
            display_text = f"[{timestamp}] 📦 数据包 #{self.total_packets}\n"
            display_text += f"   📊 大小: {len(data)} 字节\n"
            display_text += f"   📝 文本: {text_data}\n"
            
            # 显示十六进制格式
            hex_data = ' '.join(f'{b:02x}' for b in data[:16])
            if len(data) > 16:
                hex_data += " ..."
            display_text += f"   🔢 十六进制: {hex_data}\n"
            
        except Exception as e:
            display_text = f"[{timestamp}] 📦 数据包 #{self.total_packets}\n"
            display_text += f"   📊 大小: {len(data)} 字节\n"
            display_text += f"   ⚠️  二进制数据（无法解码为文本）\n"
            
            # 显示十六进制格式
            hex_data = ' '.join(f'{b:02x}' for b in data[:16])
            if len(data) > 16:
                hex_data += " ..."
            display_text += f"   🔢 十六进制: {hex_data}\n"
            
        display_text += "-" * 50 + "\n"
        
        # 在UI线程中更新显示
        self.root.after(0, lambda: self.display_message(display_text))
        
    def display_message(self, message):
        """在文本框中显示消息"""
        self.data_text.insert(tk.END, message)
        self.data_text.see(tk.END)  # 自动滚动到底部
        
    def log_message(self, message):
        """记录日志消息"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        formatted_message = f"[{timestamp}] {message}\n"
        self.display_message(formatted_message)
        
    def update_stats_loop(self):
        """持续更新统计信息"""
        while self.receiving:
            self.update_stats()
            time.sleep(1)  # 每秒更新一次
            
    def update_stats(self):
        """更新统计信息"""
        if self.start_time:
            duration = datetime.now() - self.start_time
            hours, remainder = divmod(duration.total_seconds(), 3600)
            minutes, seconds = divmod(remainder, 60)
            duration_str = f"{int(hours):02d}:{int(minutes):02d}:{int(seconds):02d}"
            
            # 计算接收速率
            if duration.total_seconds() > 0:
                rate = self.total_bytes / duration.total_seconds()
                rate_str = f"{rate:.1f} B/s"
            else:
                rate_str = "0 B/s"
                
            # 在UI线程中更新
            self.root.after(0, lambda: self.update_stats_ui(
                self.total_packets, self.total_bytes, rate_str, duration_str))
                
    def update_stats_ui(self, packets, bytes_count, rate, duration):
        """更新UI中的统计信息"""
        self.packets_var.set(f"数据包: {packets}")
        self.bytes_var.set(f"总字节: {bytes_count}")
        self.rate_var.set(f"接收速率: {rate}")
        self.duration_var.set(f"运行时间: {duration}")
        
    def clear_data(self):
        """清空接收的数据"""
        self.data_text.delete(1.0, tk.END)
        self.log_message("🗑️  数据已清空")
        
    def save_data(self):
        """保存接收的数据到文件"""
        try:
            filename = f"dji_usb_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
            with open(filename, 'w', encoding='utf-8') as f:
                f.write(self.data_text.get(1.0, tk.END))
            
            messagebox.showinfo("保存成功", f"数据已保存到: {filename}")
            self.log_message(f"💾 数据已保存到文件: {filename}")
            
        except Exception as e:
            messagebox.showerror("保存错误", f"无法保存数据: {str(e)}")
            
    def show_about(self):
        """显示关于信息"""
        about_text = """DJI遥控器USB数据接收器

版本: 1.0
作者: USB数据接收开发
日期: 2024年1月1日

功能说明:
- 接收DJI遥控器通过USB发送的数据
- 支持文本和二进制数据显示
- 实时统计接收数据量
- 自动保存接收记录

使用说明:
1. 连接DJI遥控器到PC
2. 选择正确的串口
3. 点击"开始接收"
4. 在DJI遥控器上发送数据

依赖说明:
- 需要安装pyserial库: pip install pyserial
- 如果没有安装，程序将进入模拟模式"""
        
        messagebox.showinfo("关于", about_text)
        
    def on_closing(self):
        """程序关闭时的清理工作"""
        self.stop_receive()
        self.root.destroy()

def main():
    """主函数"""
    root = tk.Tk()
    app = USBDataReceiver(root)
    
    # 设置关闭事件处理
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    
    # 启动主循环
    root.mainloop()

if __name__ == "__main__":
    main()