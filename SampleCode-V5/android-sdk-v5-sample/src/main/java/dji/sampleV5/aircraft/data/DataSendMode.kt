package dji.sampleV5.aircraft.data

/**
 * 数据发送模式枚举
 * 
 * @author Payload开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
enum class DataSendMode {
    MANUAL_ONLY,      // 仅发送手动输入的数据
    AUTO_ONLY,        // 仅自动发送PSDK数据
    BOTH              // 同时发送手动数据和PSDK数据
}