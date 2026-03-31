#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DJI遥控器USB数据接收器（Android设备模式）
专门用于接收DJI遥控器作为USB设备发送的数据

注意：DJI遥控器作为USB设备连接到PC，需要使用ADB或libusb进行通信

作者: USB数据接收开发
日期: 2024年1月1日
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import threading
import time
import subprocess
import sys
import os
from datetime import datetime

class AndroidUSBReceiver:
    def __init__(self, root):
        self.root = root
        self.root.title("DJI遥控器USB数据接收器（Android模式）")
        self.root.geometry("800x600")
        self.root.configure(bg='#f0f0f0')
        
        # 接收状态
        self.receiving = False
        self.adb_process = None
        self.receive_thread = None
        
        # 数据统计
        self.total_packets = 0
        self.total_bytes = 0
        self.start_time = None
        
        self.setup_ui()
        self.check_adb()
        
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
        title_label = ttk.Label(main_frame, text="DJI遥控器USB数据接收器（Android模式）", 
                               font=('Arial', 14, 'bold'))
        title_label.grid(row=0, column=0, columnspan=3, pady=(0, 20))
        
        # ADB状态区域
        adb_frame = ttk.LabelFrame(main_frame, text="ADB连接状态", padding="10")
        adb_frame.grid(row=1, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(0, 10))
        adb_frame.columnconfigure(1, weight=1)
        
        # ADB状态显示
        self.adb_status_var = tk.StringVar(value="ADB状态: 正在检测...")
        adb_status_label = ttk.Label(adb_frame, textvariable=self.adb_status_var)
        adb_status_label.grid(row=0, column=0, columnspan=2, sticky=tk.W)
        
        # 设备信息
        self.device_info_var = tk.StringVar(value="设备: 未连接")
        device_info_label = ttk.Label(adb_frame, textvariable=self.device_info_var)
        device_info_label.grid(row=1, column=0, columnspan=2, sticky=tk.W, pady=(5, 0))
        
        # 连接控制区域
        control_frame = ttk.LabelFrame(main_frame, text="数据接收控制", padding="10")
        control_frame.grid(row=2, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(0, 10))
        control_frame.columnconfigure(1, weight=1)
        
        # 接收模式选择
        ttk.Label(control_frame, text="接收模式:").grid(row=0, column=0, sticky=tk.W, padx=(0, 5))
        self.mode_var = tk.StringVar(value="ADB Logcat")
        mode_combo = ttk.Combobox(control_frame, textvariable=self.mode_var, 
                                 values=["ADB Logcat", "模拟数据"], width=15)
        mode_combo.grid(row=0, column=1, sticky=(tk.W, tk.E), padx=(0, 10))
        
        # 连接/断开按钮
        self.connect_btn = ttk.Button(control_frame, text="开始接收", command=self.toggle_receive)
        self.connect_btn.grid(row=0, column=2, padx=(0, 10))
        
        # 刷新设备按钮
        refresh_btn = ttk.Button(control_frame, text="刷新设备", command=self.refresh_devices)
        refresh_btn.grid(row=0, column=3)
        
        # 状态显示
        self.status_var = tk.StringVar(value="状态: 未连接")
        status_label = ttk.Label(control_frame, textvariable=self.status_var)
        status_label.grid(row=1, column=0, columnspan=4, sticky=tk.W, pady=(10, 0))
        
        # 数据统计区域
        stats_frame = ttk.LabelFrame(main_frame, text="数据统计", padding="10")
        stats_frame.grid(row=3, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(0, 10))
        
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
        data_frame.grid(row=4, column=0, columnspan=3, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(0, 10))
        data_frame.columnconfigure(0, weight=1)
        data_frame.rowconfigure(0, weight=1)
        
        # 数据文本框
        self.data_text = scrolledtext.ScrolledText(data_frame, width=80, height=15, 
                                                  font=('Consolas', 9))
        self.data_text.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # 控制按钮区域
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=5, column=0, columnspan=3, pady=(10, 0))
        
        # 清空按钮
        clear_btn = ttk.Button(button_frame, text="清空数据", command=self.clear_data)
        clear_btn.grid(row=0, column=0, padx=(0, 10))
        
        # 保存按钮
        save_btn = ttk.Button(button_frame, text="保存数据", command=self.save_data)
        save_btn.grid(row=0, column=1, padx=(0, 10))
        
        # 关于按钮
        about_btn = ttk.Button(button_frame, text="关于", command=self.show_about)
        about_btn.grid(row=0, column=2)
        
    def check_adb(self):
        """检查ADB连接状态"""
        def check():
            try:
                # 检查ADB是否可用
                result = subprocess.run(['adb', 'version'], capture_output=True, text=True)
                if result.returncode == 0:
                    self.root.after(0, lambda: self.adb_status_var.set("ADB状态: 已就绪"))
                    
                    # 检查设备连接
                    devices_result = subprocess.run(['adb', 'devices'], capture_output=True, text=True)
                    if 'device' in devices_result.stdout:
                        lines = devices_result.stdout.strip().split('\n')
                        for line in lines[1:]:  # 跳过标题行
                            if 'device' in line and not line.startswith('*'):
                                device_id = line.split('\t')[0]
                                self.root.after(0, lambda: self.device_info_var.set(f"设备: {device_id}"))
                                break
                    else:
                        self.root.after(0, lambda: self.device_info_var.set("设备: 未检测到设备"))
                else:
                    self.root.after(0, lambda: self.adb_status_var.set("ADB状态: 未找到ADB"))
                    
            except Exception as e:
                self.root.after(0, lambda: self.adb_status_var.set("ADB状态: 错误 - 未安装ADB"))
        
        # 在后台线程中检查
        threading.Thread(target=check, daemon=True).start()
        
    def refresh_devices(self):
        """刷新设备列表"""
        self.check_adb()
        
    def toggle_receive(self):
        """切换接收状态"""
        if not self.receiving:
            self.start_receive()
        else:
            self.stop_receive()
            
    def start_receive(self):
        """开始接收数据"""
        mode = self.mode_var.get()
        
        try:
            self.receiving = True
            self.connect_btn.config(text="停止接收")
            
            # 重置统计
            self.total_packets = 0
            self.total_bytes = 0
            self.start_time = datetime.now()
            self.update_stats()
            
            if mode == "ADB Logcat":
                self.status_var.set("状态: 正在通过ADB接收数据")
                # 启动ADB logcat接收
                self.receive_thread = threading.Thread(target=self.receive_adb_logcat, daemon=True)
                self.receive_thread.start()
            else:
                self.status_var.set("状态: 模拟接收数据")
                # 启动模拟数据生成
                self.receive_thread = threading.Thread(target=self.simulate_data, daemon=True)
                self.receive_thread.start()
            
            # 启动统计更新线程
            self.stats_thread = threading.Thread(target=self.update_stats_loop, daemon=True)
            self.stats_thread.start()
            
            self.log_message("🚀 USB数据接收器已启动")
            self.log_message(f"📡 接收模式: {mode}")
            self.log_message("⏳ 等待DJI遥控器发送数据...")
            
        except Exception as e:
            messagebox.showerror("启动错误", f"无法启动接收器: {str(e)}")
            
    def stop_receive(self):
        """停止接收数据"""
        self.receiving = False
        
        if self.adb_process:
            self.adb_process.terminate()
            self.adb_process = None
            
        self.connect_btn.config(text="开始接收")
        self.status_var.set("状态: 已停止接收")
        
        self.log_message("🛑 USB数据接收器已停止")
        
    def receive_adb_logcat(self):
        """通过ADB logcat接收数据"""
        try:
            # 启动ADB logcat进程，过滤USB相关日志
            self.adb_process = subprocess.Popen(
                ['adb', 'logcat', '-s', 'USBSendData', 'USB', 'DJI'],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                universal_newlines=True
            )
            
            self.log_message("🔗 已连接到DJI遥控器")
            self.log_message("📡 正在监听USB数据发送日志...")
            
            # 读取输出
            for line in iter(self.adb_process.stdout.readline, ''):
                if not self.receiving:
                    break
                    
                if line.strip():
                    self.process_logcat_line(line)
                    
        except Exception as e:
            if self.receiving:
                self.log_message(f"❌ ADB接收错误: {str(e)}")
                
    def process_logcat_line(self, line):
        """处理logcat日志行"""
        self.total_packets += 1
        self.total_bytes += len(line.encode('utf-8'))
        
        # 获取当前时间
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        
        # 解析logcat行
        display_text = f"[{timestamp}] 📦 日志条目 #{self.total_packets}\n"
        display_text += f"   📊 大小: {len(line)} 字符\n"
        display_text += f"   📝 内容: {line.strip()}\n"
        display_text += "-" * 50 + "\n"
        
        # 在UI线程中更新显示
        self.root.after(0, lambda: self.display_message(display_text))
        
    def simulate_data(self):
        """模拟数据生成（用于测试）"""
        test_messages = [
            "USB数据发送测试 - 数据包 #1",
            "DJI遥控器USB传输测试", 
            "Android USB Accessory模式工作正常",
            "USB数据接收器运行中...",
            "实时数据流传输测试"
        ]
        
        count = 0
        while self.receiving:
            # 每2秒发送一条模拟数据
            time.sleep(2)
            
            if count < len(test_messages):
                message = test_messages[count]
                self.total_packets += 1
                self.total_bytes += len(message.encode('utf-8'))
                
                timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
                display_text = f"[{timestamp}] 📦 模拟数据包 #{self.total_packets}\n"
                display_text += f"   📊 大小: {len(message)} 字符\n"
                display_text += f"   📝 内容: {message}\n"
                display_text += "-" * 50 + "\n"
                
                self.root.after(0, lambda txt=display_text: self.display_message(txt))
                count += 1
            else:
                # 循环发送
                count = 0
                
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
            filename = f"dji_android_usb_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
            with open(filename, 'w', encoding='utf-8') as f:
                f.write(self.data_text.get(1.0, tk.END))
            
            messagebox.showinfo("保存成功", f"数据已保存到: {filename}")
            self.log_message(f"💾 数据已保存到文件: {filename}")
            
        except Exception as e:
            messagebox.showerror("保存错误", f"无法保存数据: {str(e)}")
            
    def show_about(self):
        """显示关于信息"""
        about_text = """DJI遥控器USB数据接收器（Android模式）

版本: 1.0
作者: USB数据接收开发
日期: 2024年1月1日

功能说明:
- 通过ADB接收DJI遥控器发送的USB数据
- 监控Android设备的logcat日志
- 实时统计接收数据量
- 支持模拟数据测试

使用说明:
1. 连接DJI遥控器到PC并启用USB调试
2. 确保已安装ADB工具
3. 选择"ADB Logcat"模式
4. 点击"开始接收"
5. 在DJI遥控器上发送数据

ADB安装说明:
- 下载Android SDK Platform Tools
- 或将adb.exe添加到系统PATH环境变量"""
        
        messagebox.showinfo("关于", about_text)
        
    def on_closing(self):
        """程序关闭时的清理工作"""
        self.stop_receive()
        self.root.destroy()

def main():
    """主函数"""
    root = tk.Tk()
    app = AndroidUSBReceiver(root)
    
    # 设置关闭事件处理
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    
    # 启动主循环
    root.mainloop()

if __name__ == "__main__":
    main()