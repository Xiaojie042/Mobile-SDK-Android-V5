package dji.v5.ux.payload.data;

/**
 * 数据格式模式枚举
 */
public enum DataFormatMode {
    RAW_DATA_ONLY,      // 仅原始数据包（不包含格式信息）
    PSDK_FORMAT,        // PSDK数据格式（包含设备信息）
    MIXED_FORMAT        // 混合格式（包含设备信息和用户数据）
}
