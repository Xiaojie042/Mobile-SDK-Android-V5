package dji.sampleV5.aircraft.data

/**
 * USB协议类型枚举
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
enum class USBProtocol {
    BULK,    // 批量传输
    CONTROL, // 控制传输
    INTERRUPT, // 中断传输
    NETWORK_FORWARD // 网络转发模式
}