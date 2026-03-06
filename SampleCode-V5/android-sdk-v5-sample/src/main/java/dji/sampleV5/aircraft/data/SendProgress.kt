package dji.sampleV5.aircraft.data

/**
 * 数据发送进度状态
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
sealed class SendProgress {
    object IDLE : SendProgress()
    object STARTED : SendProgress()
    data class PROGRESS(val current: Int, val total: Int) : SendProgress()
    data class COMPLETED(val bytesSent: Int) : SendProgress()
    data class ERROR(val message: String?) : SendProgress()
}