package dji.sampleV5.aircraft.data

/**
 * 网络连接状态枚举
 * 
 * @author Payload开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
enum class NetworkConnectionState {
    DISCONNECTED,    // 未连接
    CONNECTING,      // 连接中
    CONNECTED,       // 已连接
    ERROR            // 错误状态
}