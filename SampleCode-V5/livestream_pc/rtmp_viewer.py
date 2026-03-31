import queue
import threading
import time
import tkinter as tk
from tkinter import messagebox, ttk

try:
    import cv2
except Exception as exc:
    cv2 = None
    CV2_IMPORT_ERROR = exc
else:
    CV2_IMPORT_ERROR = None

try:
    from PIL import Image, ImageTk
except Exception as exc:
    Image = None
    ImageTk = None
    PIL_IMPORT_ERROR = exc
else:
    PIL_IMPORT_ERROR = None


class StreamWorker(threading.Thread):
    def __init__(self, url, frame_queue, status_queue, stop_event):
        super().__init__(daemon=True)
        self.url = url
        self.frame_queue = frame_queue
        self.status_queue = status_queue
        self.stop_event = stop_event
        self.cap = None
        self.fail_count = 0

    def run(self):
        if cv2 is None:
            self._status("OpenCV import failed. Install opencv-python.")
            return

        self._status(f"Connecting: {self.url}")

        while not self.stop_event.is_set():
            if self.cap is None or not self.cap.isOpened():
                self.cap = cv2.VideoCapture(self.url, cv2.CAP_FFMPEG)
                if not self.cap.isOpened():
                    self._status("Open failed. Check RTMP server or stream URL. Retrying in 2s...")
                    time.sleep(2.0)
                    continue
                self._status("Streaming...")
                self.fail_count = 0

            ok, frame = self.cap.read()
            if not ok or frame is None:
                self.fail_count += 1
                if self.fail_count >= 30:
                    self._status("Stream read failed. Reconnecting...")
                    self._release()
                    time.sleep(1.0)
                else:
                    time.sleep(0.03)
                continue

            self.fail_count = 0
            try:
                while not self.frame_queue.empty():
                    self.frame_queue.get_nowait()
            except queue.Empty:
                pass
            self.frame_queue.put(frame)

        self._release()
        self._status("Stopped.")

    def _status(self, text):
        try:
            self.status_queue.put_nowait(text)
        except queue.Full:
            pass

    def _release(self):
        if self.cap is not None:
            try:
                self.cap.release()
            except Exception:
                pass
        self.cap = None


class RTMPViewerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("RTMP Live Viewer")
        self.root.geometry("1120x720")

        self.frame_queue = queue.Queue(maxsize=1)
        self.status_queue = queue.Queue(maxsize=10)
        self.stop_event = threading.Event()
        self.worker = None
        self.image_ref = None

        self._build_ui()
        self._schedule_ui_update()

    def _build_ui(self):
        main = ttk.Frame(self.root, padding=10)
        main.pack(fill=tk.BOTH, expand=True)

        top = ttk.Frame(main)
        top.pack(fill=tk.X)

        ttk.Label(top, text="RTMP URL:").pack(side=tk.LEFT)
        self.url_var = tk.StringVar(value="rtmp://127.0.0.1:1935/live/stream")
        self.url_entry = ttk.Entry(top, textvariable=self.url_var, width=86)
        self.url_entry.pack(side=tk.LEFT, padx=8, fill=tk.X, expand=True)

        btn_frame = ttk.Frame(main)
        btn_frame.pack(fill=tk.X, pady=8)

        self.btn_start = ttk.Button(btn_frame, text="Start", command=self.start_stream)
        self.btn_start.pack(side=tk.LEFT)
        self.btn_stop = ttk.Button(btn_frame, text="Stop", command=self.stop_stream, state=tk.DISABLED)
        self.btn_stop.pack(side=tk.LEFT, padx=6)

        self.status_var = tk.StringVar(value="Idle.")
        ttk.Label(btn_frame, textvariable=self.status_var).pack(side=tk.LEFT, padx=12)

        self.canvas = tk.Canvas(main, bg="#111111", highlightthickness=0)
        self.canvas.pack(fill=tk.BOTH, expand=True, pady=6)

        tips = (
            "Usage:\n"
            "  1. Start an RTMP server on the PC, for example SRS or nginx-rtmp.\n"
            "  2. In the DJI Live tool, push to: rtmp://<PC_IP>:1935/live/stream\n"
            "  3. In this viewer, open the same RTMP URL to verify the incoming picture.\n"
            "Common check:\n"
            "  If it keeps retrying, confirm port 1935 is open and the RTMP server is already running."
        )
        ttk.Label(main, text=tips, foreground="#666666", justify=tk.LEFT).pack(anchor=tk.W, pady=4)

    def start_stream(self):
        if cv2 is None:
            messagebox.showerror("Missing dependency", f"OpenCV import failed: {CV2_IMPORT_ERROR}")
            return
        if Image is None or ImageTk is None:
            messagebox.showerror("Missing dependency", f"Pillow import failed: {PIL_IMPORT_ERROR}")
            return

        url = self.url_var.get().strip()
        if not url:
            messagebox.showwarning("Input required", "Please input RTMP URL.")
            return

        if self.worker and self.worker.is_alive():
            messagebox.showinfo("Streaming", "Stream is already running.")
            return

        self.stop_event.clear()
        self.worker = StreamWorker(
            url=url,
            frame_queue=self.frame_queue,
            status_queue=self.status_queue,
            stop_event=self.stop_event,
        )
        self.worker.start()
        self.btn_start.config(state=tk.DISABLED)
        self.btn_stop.config(state=tk.NORMAL)
        self.status_var.set("Starting...")

    def stop_stream(self):
        self.stop_event.set()
        self.btn_start.config(state=tk.NORMAL)
        self.btn_stop.config(state=tk.DISABLED)
        self.status_var.set("Stopping...")

    def _schedule_ui_update(self):
        self._update_status()
        self._update_frame()
        self.root.after(30, self._schedule_ui_update)

    def _update_status(self):
        try:
            while True:
                status = self.status_queue.get_nowait()
                self.status_var.set(status)
        except queue.Empty:
            pass

    def _update_frame(self):
        if Image is None or ImageTk is None or cv2 is None:
            return

        try:
            frame = self.frame_queue.get_nowait()
        except queue.Empty:
            return

        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        image = Image.fromarray(frame_rgb)
        canvas_w = max(self.canvas.winfo_width(), 1)
        canvas_h = max(self.canvas.winfo_height(), 1)
        image = image.resize(self._fit_size(image.size, (canvas_w, canvas_h)), Image.LANCZOS)
        self.image_ref = ImageTk.PhotoImage(image=image)
        self.canvas.delete("all")
        self.canvas.create_image(canvas_w // 2, canvas_h // 2, image=self.image_ref, anchor=tk.CENTER)

    @staticmethod
    def _fit_size(src_size, dst_size):
        src_w, src_h = src_size
        dst_w, dst_h = dst_size
        if src_w == 0 or src_h == 0:
            return dst_size
        ratio = min(dst_w / src_w, dst_h / src_h)
        return max(1, int(src_w * ratio)), max(1, int(src_h * ratio))


def main():
    root = tk.Tk()
    app = RTMPViewerApp(root)
    root.protocol("WM_DELETE_WINDOW", lambda: (app.stop_stream(), root.destroy()))
    root.mainloop()


if __name__ == "__main__":
    main()
