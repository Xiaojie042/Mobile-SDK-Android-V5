package dji.sampleV5.aircraft.data

/**
 * 数据格式模式枚举
 * 
 * @author Payload开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
enum class DataFormatMode {
    RAW_DATA_ONLY,      // 仅原始数据包（不包含格式信息）
    PSDK_FORMAT,        // PSDK数据格式（包含设备信息）
    MIXED_FORMAT        // 混合格式（包含设备信息和用户数据）
}