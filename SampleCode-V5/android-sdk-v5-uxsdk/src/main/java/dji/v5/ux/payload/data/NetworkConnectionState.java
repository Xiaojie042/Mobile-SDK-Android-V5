package dji.v5.ux.payload.data;

/**
 * 网络连接状态枚举
 */
public enum NetworkConnectionState {
    DISCONNECTED,    // 未连接
    CONNECTING,      // 连接中
    CONNECTED,       // 已连接
    ERROR            // 错误状态
}
