import sys
import os

print(f"Python 解释器路径: {sys.executable}")
print(f"当前工作目录: {os.getcwd()}")
print("\n模块搜索路径:")
for i, path in enumerate(sys.path):
    print(f"  {i}: {path}")

try:
    import pywinusb.winusb
    print("\n✅ pywinusb 导入成功！")
except ImportError as e:
    print(f"\n❌ 导入失败: {e}")