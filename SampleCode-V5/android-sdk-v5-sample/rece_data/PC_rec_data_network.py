#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DJI USB数据接收器 - PC端图形界面版
用于接收DJI遥控器通过网络转发的USB数据

使用方法:
1. 配置监听IP和端口（默认0.0.0.0:8888）
2. 点击“启动服务器”按钮
3. 在DJI遥控器上启动网络转发，连接到PC的IP和端口
4. 接收到的数据将实时显示

作者: USB开发
日期: 2024/1/1
"""

import socket
import threading
import time
import json
from datetime import datetime
import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import queue

# 尝试获取本地IP供参考
def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip.startswith(("192.168.", "10.", "172.")):
            return ip
    except:
        pass
    return "127.0.0.1"

class USBDataReceiverGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("DJI USB数据接收器 - PC端")
        self.root.geometry("900x700")
        self.root.resizable(True, True)

        # 网络服务器相关
        self.server_thread = None
        self.running = False
        self.socket = None
        self.client_socket = None

        # 数据统计
        self.total_bytes = 0
        self.total_packets = 0
        self.start_time = None

        # 用于线程安全地传递数据到GUI
        self.data_queue = queue.Queue()

        # 创建界面组件
        self.create_widgets()

        # 定期处理队列中的数据更新GUI
        self.poll_data_queue()

        # 窗口关闭时清理
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)

    def create_widgets(self):
        # 主框架
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # 配置区域
        config_frame = ttk.LabelFrame(main_frame, text="服务器配置", padding="10")
        config_frame.pack(fill=tk.X, pady=5)

        ttk.Label(config_frame, text="监听IP:").grid(row=0, column=0, sticky=tk.W, padx=5)
        self.ip_var = tk.StringVar(value="0.0.0.0")
        ip_entry = ttk.Entry(config_frame, textvariable=self.ip_var, width=15)
        ip_entry.grid(row=0, column=1, padx=5)

        ttk.Label(config_frame, text="端口:").grid(row=0, column=2, sticky=tk.W, padx=5)
        self.port_var = tk.IntVar(value=8888)
        port_entry = ttk.Entry(config_frame, textvariable=self.port_var, width=8)
        port_entry.grid(row=0, column=3, padx=5)

        self.save_var = tk.BooleanVar(value=False)
        save_check = ttk.Checkbutton(config_frame, text="保存数据到文件", variable=self.save_var)
        save_check.grid(row=0, column=4, padx=10)

        # 按钮
        self.start_btn = ttk.Button(config_frame, text="启动服务器", command=self.start_server)
        self.start_btn.grid(row=0, column=5, padx=5)
        self.stop_btn = ttk.Button(config_frame, text="停止服务器", command=self.stop_server, state=tk.DISABLED)
        self.stop_btn.grid(row=0, column=6, padx=5)

        # 本机IP提示
        local_ip = get_local_ip()
        ttk.Label(config_frame, text=f"本机IP参考: {local_ip}", foreground="blue").grid(row=1, column=0, columnspan=7, sticky=tk.W, pady=5)

        # 统计信息区域
        stats_frame = ttk.LabelFrame(main_frame, text="统计信息", padding="10")
        stats_frame.pack(fill=tk.X, pady=5)

        self.stats_vars = {}
        stats_labels = [
            ("运行时间:", "0 秒", "time"),
            ("数据包总数:", "0", "packets"),
            ("总字节数:", "0", "bytes"),
            ("数据包速率:", "0 包/秒", "packet_rate"),
            ("字节速率:", "0 字节/秒", "byte_rate")
        ]
        for i, (label, default, key) in enumerate(stats_labels):
            ttk.Label(stats_frame, text=label).grid(row=i//3, column=(i%3)*2, sticky=tk.W, padx=5)
            self.stats_vars[key] = tk.StringVar(value=default)
            ttk.Label(stats_frame, textvariable=self.stats_vars[key], foreground="green").grid(
                row=i//3, column=(i%3)*2+1, sticky=tk.W, padx=5)

        # 数据包显示区域
        display_frame = ttk.LabelFrame(main_frame, text="接收到的数据包", padding="10")
        display_frame.pack(fill=tk.BOTH, expand=True, pady=5)

        # 创建带滚动条的文本框
        self.display_text = scrolledtext.ScrolledText(display_frame, wrap=tk.WORD, width=100, height=25)
        self.display_text.pack(fill=tk.BOTH, expand=True)

        # 配置文本标签样式
        self.display_text.tag_config("timestamp", foreground="blue")
        self.display_text.tag_config("info", foreground="darkgreen")
        self.display_text.tag_config("hex", foreground="purple")
        self.display_text.tag_config("text", foreground="black")

        # 清空显示按钮
        btn_frame = ttk.Frame(display_frame)
        btn_frame.pack(fill=tk.X, pady=5)
        ttk.Button(btn_frame, text="清空显示", command=self.clear_display).pack(side=tk.RIGHT)

        # 状态栏
        self.status_var = tk.StringVar(value="就绪")
        status_bar = ttk.Label(self.root, textvariable=self.status_var, relief=tk.SUNKEN, anchor=tk.W)
        status_bar.pack(side=tk.BOTTOM, fill=tk.X)

    def log_to_display(self, text, tag=None):
        """安全地在显示区域添加文本（从主线程调用）"""
        self.display_text.insert(tk.END, text + "\n", tag) if tag else self.display_text.insert(tk.END, text + "\n")
        self.display_text.see(tk.END)  # 自动滚动到底部

    def clear_display(self):
        self.display_text.delete(1.0, tk.END)

    def update_stats(self):
        """更新统计标签"""
        if self.start_time:
            elapsed = time.time() - self.start_time
            self.stats_vars["time"].set(f"{elapsed:.1f} 秒")
            self.stats_vars["packets"].set(str(self.total_packets))
            self.stats_vars["bytes"].set(str(self.total_bytes))
            if elapsed > 0:
                self.stats_vars["packet_rate"].set(f"{self.total_packets/elapsed:.1f} 包/秒")
                self.stats_vars["byte_rate"].set(f"{self.total_bytes/elapsed:.1f} 字节/秒")
        # 每秒更新一次（由 poll_data_queue 触发）
        self.root.after(1000, self.update_stats)

    def poll_data_queue(self):
        """定期检查队列，更新GUI"""
        try:
            while True:
                item = self.data_queue.get_nowait()
                msg_type, data = item
                if msg_type == "packet":
                    # data: (timestamp, packet_num, size, hex_str, text_str)
                    timestamp, packet_num, size, hex_str, text_str = data
                    self.log_to_display(f"[{timestamp}] 📦 数据包 #{packet_num}", "timestamp")
                    self.log_to_display(f"   📊 大小: {size} 字节", "info")
                    self.log_to_display(f"   🔢 十六进制: {hex_str[:64]}{'...' if len(hex_str)>64 else ''}", "hex")
                    self.log_to_display(f"   📝 文本: {text_str[:100]}{'...' if len(text_str)>100 else ''}", "text")
                    self.log_to_display("")  # 空行分隔
                elif msg_type == "status":
                    self.status_var.set(data)
                elif msg_type == "client_connected":
                    self.log_to_display(f"✅ DJI遥控器已连接: {data}", "info")
                elif msg_type == "client_disconnected":
                    self.log_to_display(f"🔌 DJI遥控器断开连接: {data}", "info")
        except queue.Empty:
            pass
        self.root.after(100, self.poll_data_queue)

    def start_server(self):
        """启动服务器（由按钮触发）"""
        ip = self.ip_var.get().strip()
        port = self.port_var.get()
        save_flag = self.save_var.get()

        if not ip:
            messagebox.showerror("错误", "请输入有效的IP地址")
            return
        if port <= 0 or port > 65535:
            messagebox.showerror("错误", "端口必须在1-65535之间")
            return

        # 启动服务器线程
        self.running = True
        self.total_bytes = 0
        self.total_packets = 0
        self.start_time = time.time()

        self.server_thread = threading.Thread(
            target=self.server_worker,
            args=(ip, port, save_flag),
            daemon=True
        )
        self.server_thread.start()

        # 更新按钮状态
        self.start_btn.config(state=tk.DISABLED)
        self.stop_btn.config(state=tk.NORMAL)
        self.status_var.set(f"服务器启动在 {ip}:{port}，等待连接...")
        self.log_to_display(f"🚀 USB数据接收器已启动于 {ip}:{port}")
        self.log_to_display(f"⏰ {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

        # 开始统计更新
        self.update_stats()

    def stop_server(self):
        """停止服务器"""
        self.running = False
        if self.client_socket:
            try:
                self.client_socket.close()
            except:
                pass
            self.client_socket = None
        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None

        self.start_btn.config(state=tk.NORMAL)
        self.stop_btn.config(state=tk.DISABLED)
        self.status_var.set("服务器已停止")
        self.log_to_display("🛑 USB数据接收器已停止")

    def server_worker(self, host, port, save_to_file):
        """在后台线程中运行的服务器逻辑"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.bind((host, port))
            self.socket.listen(1)
            self.socket.settimeout(1.0)  # 允许定期检查 self.running

            self.data_queue.put(("status", f"监听 {host}:{port}"))

            data_file = None
            if save_to_file:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                filename = f"usb_data_{timestamp}.log"
                data_file = open(filename, 'a', encoding='utf-8')
                self.data_queue.put(("status", f"数据将保存到 {filename}"))

            while self.running:
                try:
                    client_socket, client_addr = self.socket.accept()
                except socket.timeout:
                    continue
                except Exception as e:
                    if self.running:
                        self.data_queue.put(("status", f"接受连接错误: {e}"))
                    break

                self.client_socket = client_socket
                self.data_queue.put(("client_connected", f"{client_addr[0]}:{client_addr[1]}"))

                # 处理客户端数据
                self.handle_client(client_socket, client_addr, data_file)

                # 客户端断开后继续等待新连接
                self.client_socket = None
                self.data_queue.put(("client_disconnected", f"{client_addr[0]}:{client_addr[1]}"))

        except Exception as e:
            self.data_queue.put(("status", f"服务器错误: {e}"))
        finally:
            if data_file:
                data_file.close()
            if self.socket:
                self.socket.close()
            self.socket = None
            # 确保按钮状态更新（但可能在主线程中通过stop_server已经处理）
            self.root.after(0, self.server_stopped_callback)

    def handle_client(self, client_socket, client_addr, data_file):
        """处理单个客户端的数据接收"""
        buffer = b''
        try:
            client_socket.settimeout(1.0)
            while self.running:
                try:
                    data = client_socket.recv(4096)
                    if not data:
                        break
                except socket.timeout:
                    continue
                except Exception as e:
                    if self.running:
                        self.data_queue.put(("status", f"接收错误: {e}"))
                    break

                buffer += data

                # 按换行符分割完整包
                while b'\n' in buffer:
                    line, buffer = buffer.split(b'\n', 1)
                    self.process_packet(line, client_addr, data_file)

        except Exception as e:
            self.data_queue.put(("status", f"客户端处理异常: {e}"))
        finally:
            client_socket.close()

    def process_packet(self, packet_data, client_addr, data_file):
        """处理单个数据包"""
        self.total_bytes += len(packet_data)
        self.total_packets += 1

        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        hex_str = packet_data.hex().upper()
        try:
            text_str = packet_data.decode('utf-8', errors='ignore')
        except:
            text_str = "<解码错误>"

        # 放入队列，由主线程更新显示
        self.data_queue.put(("packet", (timestamp, self.total_packets, len(packet_data), hex_str, text_str)))

        # 保存到文件
        if data_file:
            log_entry = {
                'timestamp': timestamp,
                'size': len(packet_data),
                'hex': hex_str,
                'text': text_str,
                'packet_number': self.total_packets
            }
            data_file.write(json.dumps(log_entry, ensure_ascii=False) + '\n')
            data_file.flush()

    def server_stopped_callback(self):
        """服务器线程结束后在主线程中调用的回调"""
        if not self.running:  # 如果是主动停止，按钮状态已更新；如果是异常停止，需要更新按钮
            self.start_btn.config(state=tk.NORMAL)
            self.stop_btn.config(state=tk.DISABLED)
            self.status_var.set("服务器已停止")

    def on_closing(self):
        """窗口关闭时清理"""
        if self.running:
            self.stop_server()
        self.root.destroy()

def main():
    root = tk.Tk()
    app = USBDataReceiverGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()