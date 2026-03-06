package dji.sampleV5.aircraft.data

/**
 * USB连接状态枚举
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
enum class USBConnectionState {
    DISCONNECTED,    // 未连接
    SCANNING,        // 扫描中
    CONNECTING,      // 连接中
    CONNECTED,       // 已连接
    ERROR            // 错误状态
}