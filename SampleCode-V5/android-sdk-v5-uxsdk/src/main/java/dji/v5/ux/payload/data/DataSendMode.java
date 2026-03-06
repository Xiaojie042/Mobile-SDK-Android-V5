package dji.v5.ux.payload.data;

/**
 * 数据发送模式枚举
 */
public enum DataSendMode {
    MANUAL_ONLY,      // 仅发送手动输入的数据
    AUTO_ONLY,        // 仅自动发送PSDK数据
    BOTH              // 同时发送手动数据和PSDK数据
}
