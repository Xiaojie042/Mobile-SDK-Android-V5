# 飞控数据转发字段说明

本文档对应 [flight_data_schema_v2.json](./flight_data_schema_v2.json)，用于说明当前项目中飞控数据转发功能实际发送的 JSON 字段、字段含义、单位和取值范围。

## 基本说明

| 项目        | 说明                                                           |
| --------- | ------------------------------------------------------------ |
| 数据类型标识    | 固定为 `flight_data`                                            |
| Schema 版本 | 固定为 `2`                                                      |
| 发送频率      | 仅支持 `1 / 5 / 10 Hz`                                          |
| 默认服务器地址   | `192.168.3.31:9999`                                          |
| 字段是否一定出现  | 不一定。当前实现中，只有 DJI SDK 返回非空值时，该字段才会被放进 JSON                    |
| 枚举输出方式    | 所有枚举字段都输出为 `enum.name()` 字符串                                 |
| 范围说明      | 标记为“机型相关”的字段，表示 DJI SDK 在当前使用方式下没有公开固定上下限，真实范围依赖机型、飞行状态或区域限制 |

## 顶层字段

| 字段                                      | 类型      | 单位       | 来源                                                         | 取值范围/说明                       |
| --------------------------------------- | ------- | -------- | ---------------------------------------------------------- | ----------------------------- |
| `type`                                  | string  | -        | 常量                                                         | 固定为 `flight_data`             |
| `schema_version`                        | integer | -        | 常量                                                         | 固定为 `2`                       |
| `timestamp`                             | string  | -        | 本地生成                                                       | 格式为 `yyyy-MM-dd HH:mm:ss.SSS` |
| `flight_mode`                           | string  | -        | `FlightControllerKey.KeyFlightMode`                        | 枚举，详见下方“常用枚举说明”               |
| `battery_percentage`                    | integer | `%`      | `BatteryKey.KeyChargeRemainingInPercent(Aggregation)`      | `0~100`                       |
| `is_flying`                             | boolean | -        | `FlightControllerKey.KeyIsFlying`                          | `true/false`                  |
| `aircraft_name`                         | string  | -        | `FlightControllerKey.KeyAircraftName`                      | 飞机名称，自由文本                     |
| `product_type`                          | string  | -        | `ProductKey.KeyProductType`                                | 枚举，机型类型                       |
| `product_firmware_version`              | string  | -        | `ProductKey.KeyFirmwareVersion`                            | 固件版本字符串                       |
| `flight_controller_serial_number`       | string  | -        | `FlightControllerKey.KeySerialNumber`                      | 飞控序列号                         |
| `flight_controller_connected`           | boolean | -        | `FlightControllerKey.KeyConnection`                        | `true/false`                  |
| `remote_controller_connected`           | boolean | -        | `RemoteControllerKey.KeyConnection`                        | `true/false`                  |
| `aircraft_heading`                      | double  | `degree` | `FlightControllerKey.KeyCompassHeading`                    | 一般可理解为 `0~360`，但仍属机型相关        |
| `relative_altitude`                     | double  | `meter`  | `FlightControllerKey.KeyAltitude`                          | 机型相关，可能为负值                    |
| `gps_satellite_count`                   | integer | `count`  | `FlightControllerKey.KeyGPSSatelliteCount`                 | `>=0`                         |
| `gps_signal_level`                      | string  | -        | `FlightControllerKey.KeyGPSSignalLevel`                    | 枚举                            |
| `flight_mode_string`                    | string  | -        | `FlightControllerKey.KeyFlightModeString`                  | SDK 返回的模式字符串                  |
| `fc_flight_mode`                        | string  | -        | `FlightControllerKey.KeyFCFlightMode`                      | 枚举                            |
| `are_motors_on`                         | boolean | -        | `FlightControllerKey.KeyAreMotorsOn`                       | `true/false`                  |
| `is_in_landing_mode`                    | boolean | -        | `FlightControllerKey.KeyIsInLandingMode`                   | `true/false`                  |
| `is_landing_confirmation_needed`        | boolean | -        | `FlightControllerKey.KeyIsLandingConfirmationNeeded`       | `true/false`                  |
| `flight_time_in_seconds`                | integer | `second` | `FlightControllerKey.KeyFlightTimeInSeconds`               | `>=0`                         |
| `go_home_height`                        | integer | `meter`  | `FlightControllerKey.KeyGoHomeHeight`                      | `>=0`，上限机型相关                  |
| `height_limit`                          | integer | `meter`  | `FlightControllerKey.KeyHeightLimit`                       | `>=0`，上限机型相关                  |
| `distance_limit_enabled`                | boolean | -        | `FlightControllerKey.KeyDistanceLimitEnabled`              | `true/false`                  |
| `distance_limit`                        | integer | `meter`  | `FlightControllerKey.KeyDistanceLimit`                     | `>=0`，上限机型相关                  |
| `battery_percent_needed_to_go_home`     | integer | `%`      | `FlightControllerKey.KeyBatteryPercentNeededToGoHome`      | `0~100`                       |
| `low_battery_warning_threshold`         | integer | `%`      | `FlightControllerKey.KeyLowBatteryWarningThreshold`        | `0~100`                       |
| `serious_low_battery_warning_threshold` | integer | `%`      | `FlightControllerKey.KeySeriousLowBatteryWarningThreshold` | `0~100`                       |
| `battery_threshold_behavior`            | string  | -        | `FlightControllerKey.KeyBatteryThresholdBehavior`          | 枚举                            |
| `auto_rth_reason`                       | string  | -        | `FlightControllerKey.KeyAutoRTHReason`                     | 枚举                            |
| `failsafe_action`                       | string  | -        | `FlightControllerKey.KeyFailsafeAction`                    | 枚举                            |
| `horizontal_speed`                      | double  | `m/s`    | 由 `velocity.x/y` 推导                                        | `>=0`                         |
| `speed_total`                           | double  | `m/s`    | 由 `velocity.x/y/z` 推导                                      | `>=0`                         |
| `altitude`                              | double  | `meter`  | `location.altitude` 的顶层重复字段                                | 与 `location.altitude` 相同      |

## attitude 姿态

| 字段               | 类型     | 单位       | 来源                                        | 取值范围/说明                    |
| ---------------- | ------ | -------- | ----------------------------------------- | -------------------------- |
| `attitude.pitch` | double | `degree` | `FlightControllerKey.KeyAircraftAttitude` | 一般可按 `-180~180` 理解，实际为机型相关 |
| `attitude.roll`  | double | `degree` | `FlightControllerKey.KeyAircraftAttitude` | 一般可按 `-180~180` 理解，实际为机型相关 |
| `attitude.yaw`   | double | `degree` | `FlightControllerKey.KeyAircraftAttitude` | 一般可按 `-180~180` 理解，实际为机型相关 |

## location / home_location 坐标

| 字段                        | 类型     | 单位       | 来源                                          | 取值范围/说明    |
| ------------------------- | ------ | -------- | ------------------------------------------- | ---------- |
| `location.latitude`       | double | `degree` | `FlightControllerKey.KeyAircraftLocation3D` | `-90~90`   |
| `location.longitude`      | double | `degree` | `FlightControllerKey.KeyAircraftLocation3D` | `-180~180` |
| `location.altitude`       | double | `meter`  | `FlightControllerKey.KeyAircraftLocation3D` | 机型相关       |
| `home_location.latitude`  | double | `degree` | `FlightControllerKey.KeyHomeLocation`       | `-90~90`   |
| `home_location.longitude` | double | `degree` | `FlightControllerKey.KeyHomeLocation`       | `-180~180` |

## velocity 速度

| 字段                          | 类型     | 单位    | 来源                                        | 取值范围/说明      |
| --------------------------- | ------ | ----- | ----------------------------------------- | ------------ |
| `velocity.x`                | double | `m/s` | `FlightControllerKey.KeyAircraftVelocity` | 有符号速度分量，机型相关 |
| `velocity.y`                | double | `m/s` | `FlightControllerKey.KeyAircraftVelocity` | 有符号速度分量，机型相关 |
| `velocity.z`                | double | `m/s` | `FlightControllerKey.KeyAircraftVelocity` | 有符号速度分量，机型相关 |
| `velocity.horizontal_speed` | double | `m/s` | `sqrt(x^2+y^2)`                           | `>=0`        |
| `velocity.total_speed`      | double | `m/s` | `sqrt(x^2+y^2+z^2)`                       | `>=0`        |

## low_battery_rth_info 低电返航信息

| 字段                                                       | 类型      | 单位       | 来源                                         | 取值范围/说明 |
| -------------------------------------------------------- | ------- | -------- | ------------------------------------------ | ------- |
| `low_battery_rth_info.battery_percent_needed_to_land`    | integer | `%`      | `FlightControllerKey.KeyLowBatteryRTHInfo` | `0~100` |
| `low_battery_rth_info.battery_percent_needed_to_go_home` | integer | `%`      | `FlightControllerKey.KeyLowBatteryRTHInfo` | `0~100` |
| `low_battery_rth_info.remaining_flight_time`             | integer | `second` | `FlightControllerKey.KeyLowBatteryRTHInfo` | `>=0`   |

## wind 风场信息

| 字段               | 类型      | 单位    | 来源                                     | 取值范围/说明 |
| ---------------- | ------- | ----- | -------------------------------------- | ------- |
| `wind.speed`     | integer | `m/s` | `FlightControllerKey.KeyWindSpeed`     | `>=0`   |
| `wind.direction` | string  | -     | `FlightControllerKey.KeyWindDirection` | 枚举      |
| `wind.warning`   | string  | -     | `FlightControllerKey.KeyWindWarning`   | 枚举      |

## vps_status 视觉/超声状态

| 字段                                      | 类型      | 单位   | 来源                                               | 取值范围/说明      |
| --------------------------------------- | ------- | ---- | ------------------------------------------------ | ------------ |
| `vps_status.vision_positioning_enabled` | boolean | -    | `FlightAssistantKey.KeyVisionPositioningEnabled` | `true/false` |
| `vps_status.ultrasonic_used`            | boolean | -    | `FlightControllerKey.KeyIsUltrasonicUsed`        | `true/false` |
| `vps_status.ultrasonic_height_cm`       | integer | `cm` | `FlightControllerKey.KeyUltrasonicHeight`        | `>=0`        |
| `vps_status.landing_protection_state`   | string  | -    | `FlightAssistantKey.KeyLandingProtectionState`   | 枚举           |

## battery_status 电池状态

| 字段                                                     | 类型      | 单位           | 来源                                                        | 取值范围/说明                     |
| ------------------------------------------------------ | ------- | ------------ | --------------------------------------------------------- | --------------------------- |
| `battery_status.aggregate_percentage`                  | integer | `%`          | `BatteryKey.KeyChargeRemainingInPercent(Aggregation)`     | `0~100`                     |
| `battery_status.connected_count`                       | integer | `count`      | `BatteryKey.KeyNumberOfConnectedBatteries(Aggregation)`   | `>=0`                       |
| `battery_status.any_battery_disconnected`              | boolean | -            | `BatteryKey.KeyIsAnyBatteryDisconnected(Aggregation)`     | `true/false`                |
| `battery_status.cell_damaged`                          | boolean | -            | `BatteryKey.KeyIsCellDamaged(Aggregation)`                | `true/false`                |
| `battery_status.firmware_difference_detected`          | boolean | -            | `BatteryKey.KeyIsFirmwareDifferenceDetected(Aggregation)` | `true/false`                |
| `battery_status.voltage_difference_detected`           | boolean | -            | `BatteryKey.KeyIsVoltageDifferenceDetected(Aggregation)`  | `true/false`                |
| `battery_status.low_cell_voltage_detected`             | boolean | -            | `BatteryKey.KeyIsLowCellVoltageDetected(Aggregation)`     | `true/false`                |
| `battery_status.overview[].index`                      | integer | `slot_index` | `BatteryKey.KeyBatteryOverviews(Aggregation)`             | `>=0`                       |
| `battery_status.overview[].is_connected`               | boolean | -            | `BatteryKey.KeyBatteryOverviews(Aggregation)`             | `true/false`                |
| `battery_status.main_battery.index`                    | integer | -            | 固定值                                                       | 固定为 `0`                     |
| `battery_status.secondary_battery.index`               | integer | -            | 固定值                                                       | 固定为 `1`                     |
| `battery_status.main_battery.connected`                | boolean | -            | `BatteryKey.KeyConnection(0)`                             | `true/false`                |
| `battery_status.secondary_battery.connected`           | boolean | -            | `BatteryKey.KeyConnection(1)`                             | `true/false`                |
| `battery_status.main_battery.percentage`               | integer | `%`          | `BatteryKey.KeyChargeRemainingInPercent(0)`               | `0~100`                     |
| `battery_status.secondary_battery.percentage`          | integer | `%`          | `BatteryKey.KeyChargeRemainingInPercent(1)`               | `0~100`                     |
| `battery_status.main_battery.temperature_celsius`      | double  | `celsius`    | `BatteryKey.KeyBatteryTemperature(0)`                     | 机型相关                        |
| `battery_status.secondary_battery.temperature_celsius` | double  | `celsius`    | `BatteryKey.KeyBatteryTemperature(1)`                     | 机型相关                        |
| `battery_status.main_battery.voltage_mv`               | integer | `mV`         | `BatteryKey.KeyVoltage(0)`                                | `>=0`                       |
| `battery_status.secondary_battery.voltage_mv`          | integer | `mV`         | `BatteryKey.KeyVoltage(1)`                                | `>=0`                       |
| `battery_status.main_battery.serial_number`            | string  | -            | `BatteryKey.KeySerialNumber(0)`                           | 自由文本                        |
| `battery_status.secondary_battery.serial_number`       | string  | -            | `BatteryKey.KeySerialNumber(1)`                           | 自由文本                        |
| `battery_status.main_battery.cell_voltages_mv[]`       | integer | `mV`         | `BatteryKey.KeyCellVoltages(0)`                           | `>=0`，单芯典型值约 `3000~4500 mV` |
| `battery_status.secondary_battery.cell_voltages_mv[]`  | integer | `mV`         | `BatteryKey.KeyCellVoltages(1)`                           | `>=0`，单芯典型值约 `3000~4500 mV` |

## air_link_status 图传链路状态

| 字段                                      | 类型      | 单位  | 来源                                 | 取值范围/说明                |
| --------------------------------------- | ------- | --- | ---------------------------------- | ---------------------- |
| `air_link_status.connected`             | boolean | -   | `AirLinkKey.KeyConnection`         | `true/false`           |
| `air_link_status.down_link_quality`     | integer | -   | `AirLinkKey.KeyDownLinkQuality`    | 通常可按 `0~100` 百分比理解     |
| `air_link_status.down_link_quality_raw` | integer | -   | `AirLinkKey.KeyDownLinkQualityRaw` | 原始值，范围未公开固定            |
| `air_link_status.up_link_quality`       | integer | -   | `AirLinkKey.KeyUpLinkQuality`      | 通常可按 `0~100` 百分比理解     |
| `air_link_status.up_link_quality_raw`   | integer | -   | `AirLinkKey.KeyUpLinkQualityRaw`   | 原始值，范围未公开固定            |
| `air_link_status.link_signal_quality`   | integer | -   | `AirLinkKey.KeyLinkSignalQuality`  | 通常可按 `0~100` 百分比理解     |
| `air_link_status.dynamic_data_rate`     | double  | -   | `AirLinkKey.KeyDynamicDataRate`    | `>=0`，具体单位以 DJI 机型实现为准 |
| `air_link_status.frequency_point`       | integer | -   | `AirLinkKey.KeyFrequencyPoint`     | 频点值，范围机型相关             |
| `air_link_status.frequency_band`        | string  | -   | `AirLinkKey.KeyFrequencyBand`      | 枚举或字符串，取决于 SDK 返回类型    |

## remote_controller_status 遥控器状态

| 字段                                                | 类型      | 单位       | 来源                                                                      | 取值范围/说明                          |
| ------------------------------------------------- | ------- | -------- | ----------------------------------------------------------------------- | -------------------------------- |
| `remote_controller_status.connected`              | boolean | -        | `RemoteControllerKey.KeyConnection`                                     | `true/false`                     |
| `remote_controller_status.mode`                   | string  | -        | `RemoteControllerKey.KeyRcMachineMode`                                  | 枚举                               |
| `remote_controller_status.serial_number`          | string  | -        | `RemoteControllerKey.KeyRcRK3399SirialNumber`                           | 遥控器序列号                           |
| `remote_controller_status.battery_percentage`     | integer | `%`      | `RemoteControllerKey.KeyBatteryInfo -> BatteryInfo.getBatteryPercent()` | `0~100`                          |
| `remote_controller_status.gps.valid`              | boolean | -        | `RemoteControllerKey.KeyRcGPSInfo`                                      | `true/false`                     |
| `remote_controller_status.gps.location.latitude`  | double  | `degree` | `RemoteControllerKey.KeyRcGPSInfo`                                      | `-90~90`，仅在 `valid=true` 时可能出现   |
| `remote_controller_status.gps.location.longitude` | double  | `degree` | `RemoteControllerKey.KeyRcGPSInfo`                                      | `-180~180`，仅在 `valid=true` 时可能出现 |

## gimbal_status 云台状态

| 字段                                         | 类型      | 单位       | 来源                                          | 取值范围/说明                    |
| ------------------------------------------ | ------- | -------- | ------------------------------------------- | -------------------------- |
| `gimbal_status.main_gimbal_connected`      | boolean | -        | `GimbalKey.KeyConnection(LEFT_OR_MAIN)`     | `true/false`               |
| `gimbal_status.main_gimbal_attitude.pitch` | double  | `degree` | `GimbalKey.KeyGimbalAttitude(LEFT_OR_MAIN)` | 一般可按 `-180~180` 理解，实际为机型相关 |
| `gimbal_status.main_gimbal_attitude.roll`  | double  | `degree` | `GimbalKey.KeyGimbalAttitude(LEFT_OR_MAIN)` | 一般可按 `-180~180` 理解，实际为机型相关 |
| `gimbal_status.main_gimbal_attitude.yaw`   | double  | `degree` | `GimbalKey.KeyGimbalAttitude(LEFT_OR_MAIN)` | 一般可按 `-180~180` 理解，实际为机型相关 |

## aircraft_status 飞机扩展状态

| 字段                                            | 类型     | 单位       | 来源                              | 取值范围/说明    |
| --------------------------------------------- | ------ | -------- | ------------------------------- | ---------- |
| `aircraft_status.aircraft_location.latitude`  | double | `degree` | `location.latitude` 的重复字段       | `-90~90`   |
| `aircraft_status.aircraft_location.longitude` | double | `degree` | `location.longitude` 的重复字段      | `-180~180` |
| `aircraft_status.aircraft_location.altitude`  | double | `meter`  | `location.altitude` 的重复字段       | 机型相关       |
| `aircraft_status.home_location.latitude`      | double | `degree` | `home_location.latitude` 的重复字段  | `-90~90`   |
| `aircraft_status.home_location.longitude`     | double | `degree` | `home_location.longitude` 的重复字段 | `-180~180` |
| `aircraft_status.distance_to_home`            | double | `meter`  | 由飞机坐标和返航点坐标计算                   | `>=0`      |

## 常用枚举说明

### GPS 信号等级 `gps_signal_level`

| 枚举值          |
| ------------ |
| `LEVEL_0`    |
| `LEVEL_1`    |
| `LEVEL_2`    |
| `LEVEL_3`    |
| `LEVEL_4`    |
| `LEVEL_5`    |
| `LEVEL_10`   |
| `LEVEL_NONE` |
| `UNKNOWN`    |

### 电池阈值行为 `battery_threshold_behavior`

| 枚举值                | 说明   |
| ------------------ | ---- |
| `FLY_NORMALLY`     | 正常飞行 |
| `GO_HOME`          | 返航   |
| `LAND_IMMEDIATELY` | 立即降落 |
| `UNKNOWN`          | 未知   |

### 失控动作 `failsafe_action`

| 枚举值       | 说明  |
| --------- | --- |
| `HOVER`   | 悬停  |
| `LANDING` | 降落  |
| `GOHOME`  | 返航  |
| `UNKNOWN` | 未知  |

### 风向 `wind.direction`

| 枚举值          |
| ------------ |
| `WINDLESS`   |
| `NORTH`      |
| `NORTH_EAST` |
| `EAST`       |
| `SOUTH_EAST` |
| `SOUTH`      |
| `SOUTH_WEST` |
| `WEST`       |
| `NORTH_WEST` |
| `UNKNOWN`    |

### 风警告 `wind.warning`

| 枚举值       |
| --------- |
| `LEVEL_0` |
| `LEVEL_1` |
| `LEVEL_2` |
| `UNKNOWN` |

### 降落保护状态 `vps_status.landing_protection_state`

| 枚举值                |
| ------------------ |
| `NONE`             |
| `ANALYZING`        |
| `ANALYSIS_FAILED`  |
| `SAFE_TO_LAND`     |
| `NOT_SAFE_TO_LAND` |
| `UNKNOWN`          |

### 遥控器模式 `remote_controller_status.mode`

| 枚举值          |
| ------------ |
| `HOST`       |
| `SLAVE`      |
| `MASTER_SUB` |
| `SLAVE_SUB`  |
| `CHANNEL_A`  |
| `CHANNEL_B`  |
| `NORMAL`     |
| `UNKNOWN`    |

## 说明补充

| 项目                            | 说明                                                                             |
| ----------------------------- | ------------------------------------------------------------------------------ |
| `ProductType`                 | 机型枚举很多，完整列表请直接看 `flight_data_schema_v2.json` 中 `enum_sets.ProductType`         |
| `FlightMode` / `FCFlightMode` | 枚举值较多，完整列表请直接看 `flight_data_schema_v2.json` 中对应 `enum_sets`                    |
| `FCAutoRTHReason`             | 自动返航原因枚举很多，完整列表请直接看 `flight_data_schema_v2.json` 中 `enum_sets.FCAutoRTHReason` |
| 字段缺失                          | 如果某字段在某次报文里不存在，通常不是程序错误，而是当前机型/当前状态下 DJI SDK 没返回该值                             |
