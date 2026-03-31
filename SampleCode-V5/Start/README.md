# PSDK 设置界面 - 后端开发指南

## 📋 概述

本指南提供了实现 PSDK 设置界面后端功能的详细步骤和代码示例。

## 🏗️ 架构设计

### MVVM 架构

```
View (PayloadFragment)
    ↓
ViewModel (PayloadWidgetModel)
    ↓
Model (DJI SDK / Payload API)
```

## 📝 开发步骤

### 步骤 1: 创建 WidgetModel

创建文件：`android-sdk-v5-uxsdk/src/main/java/dji/v5/ux/core/widget/payload/PayloadWidgetModel.java`

```java
package dji.v5.ux.core.widget.payload;

import androidx.annotation.NonNull;

import dji.sdk.keyvalue.key.ComponentIndexType;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.key.PayloadKey;
import dji.v5.ux.core.base.DJISDKModel;
import dji.v5.ux.core.base.WidgetModel;
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore;
import dji.v5.ux.core.util.DataProcessor;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Payload Widget Model
 * 管理 PSDK 连接和数据通信
 */
public class PayloadWidgetModel extends WidgetModel {

    private final DataProcessor<Boolean> connectionStateProcessor;
    private final DataProcessor<String> payloadMessageProcessor;
    private ComponentIndexType currentPayloadIndex;

    public PayloadWidgetModel(@NonNull DJISDKModel djiSdkModel,
                              @NonNull ObservableInMemoryKeyedStore keyedStore) {
        super(djiSdkModel, keyedStore);
        connectionStateProcessor = DataProcessor.create(false);
        payloadMessageProcessor = DataProcessor.create("");
        currentPayloadIndex = ComponentIndexType.LEFT_OR_MAIN;
    }

    @Override
    protected void inSetup() {
        // 订阅 Payload 连接状态
        bindDataProcessor(
            KeyTools.createKey(PayloadKey.KeyConnection, currentPayloadIndex),
            connectionStateProcessor
        );
    }

    @Override
    protected void inCleanup() {
        // 清理资源
    }

    /**
     * 设置当前 Payload 索引
     */
    public void setPayloadIndex(int index) {
        switch (index) {
            case 0:
                currentPayloadIndex = ComponentIndexType.LEFT_OR_MAIN;
                break;
            case 1:
                currentPayloadIndex = ComponentIndexType.RIGHT;
                break;
            case 2:
                currentPayloadIndex = ComponentIndexType.UP;
                break;
            default:
                currentPayloadIndex = ComponentIndexType.LEFT_OR_MAIN;
        }
        restart();
    }

    /**
     * 获取连接状态
     */
    public Flowable<Boolean> getConnectionState() {
        return connectionStateProcessor.toFlowable();
    }

    /**
     * 获取 Payload 消息
     */
    public Flowable<String> getPayloadMessage() {
        return payloadMessageProcessor.toFlowable();
    }

    /**
     * 连接到 Payload
     */
    public void connectPayload() {
        // TODO: 实现连接逻辑
        // 使用 DJI SDK 的 Payload API 建立连接
    }

    /**
     * 断开 Payload 连接
     */
    public void disconnectPayload() {
        // TODO: 实现断开连接逻辑
    }

    /**
     * 发送命令到 Payload
     */
    public void sendCommand(byte[] command) {
        // TODO: 实现命令发送逻辑
        // 使用 PayloadKey.KeySendDataToPsdk
    }
}
```

### 步骤 2: 更新 PayloadFragment

更新 `PayloadFragment.java` 以集成 WidgetModel：

```java
package dji.v5.ux.core.ui.setting.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.base.DJISDKModel;
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;
import dji.v5.ux.core.widget.payload.PayloadWidgetModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class PayloadFragment extends MenuFragment {
    
    private Spinner spinnerPsdkSelection;
    private TextView tvConnectionStatus;
    private TextView tvPsdkMessage;
    private Button btnConnect;
    private Button btnClear;
    
    private PayloadWidgetModel widgetModel;
    private CompositeDisposable compositeDisposable;
    
    private int selectedPsdkIndex = 0;
    private boolean isConnected = false;
    private StringBuilder messageBuffer = new StringBuilder();
    
    @Override
    protected String getPreferencesTitle() {
        return StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_payload_setting_title);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.uxsdk_fragment_setting_menu_payload_layout;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化 WidgetModel
        widgetModel = new PayloadWidgetModel(
            DJISDKModel.getInstance(),
            ObservableInMemoryKeyedStore.getInstance()
        );
        compositeDisposable = new CompositeDisposable();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupSpinner();
        setupButtons();
        subscribeToModel();
    }
    
    private void initViews(View view) {
        spinnerPsdkSelection = view.findViewById(R.id.spinner_psdk_selection);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        tvPsdkMessage = view.findViewById(R.id.tv_psdk_message);
        btnConnect = view.findViewById(R.id.btn_connect);
        btnClear = view.findViewById(R.id.btn_clear);
    }
    
    private void setupSpinner() {
        List<String> psdkList = new ArrayList<>();
        psdkList.add(StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_payload_psdk_1));
        psdkList.add(StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_payload_psdk_2));
        psdkList.add(StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_payload_psdk_3));
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            psdkList
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPsdkSelection.setAdapter(adapter);
        
        spinnerPsdkSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPsdkIndex = position;
                widgetModel.setPayloadIndex(position);
                appendMessage("Selected: " + psdkList.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }
    
    private void setupButtons() {
        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnectPsdk();
            } else {
                connectPsdk();
            }
        });
        
        btnClear.setOnClickListener(v -> clearMessages());
    }
    
    private void subscribeToModel() {
        // 订阅连接状态
        compositeDisposable.add(
            widgetModel.getConnectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(connected -> {
                    isConnected = connected;
                    updateConnectionStatus();
                    if (connected) {
                        appendMessage("Connected to PSDK " + (selectedPsdkIndex + 1));
                    } else {
                        appendMessage("Disconnected from PSDK " + (selectedPsdkIndex + 1));
                    }
                })
        );
        
        // 订阅 Payload 消息
        compositeDisposable.add(
            widgetModel.getPayloadMessage()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(message -> {
                    if (message != null && !message.isEmpty()) {
                        appendMessage(message);
                    }
                })
        );
    }
    
    private void connectPsdk() {
        appendMessage("Connecting to PSDK " + (selectedPsdkIndex + 1) + "...");
        widgetModel.connectPayload();
    }
    
    private void disconnectPsdk() {
        appendMessage("Disconnecting from PSDK " + (selectedPsdkIndex + 1) + "...");
        widgetModel.disconnectPayload();
    }
    
    private void updateConnectionStatus() {
        if (isConnected) {
            tvConnectionStatus.setText(R.string.uxsdk_payload_connected);
            btnConnect.setText(R.string.uxsdk_payload_disconnect);
        } else {
            tvConnectionStatus.setText(R.string.uxsdk_payload_disconnected);
            btnConnect.setText(R.string.uxsdk_payload_connect);
        }
    }
    
    private void appendMessage(String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        messageBuffer.insert(0, "[" + timestamp + "] " + message + "\n");
        
        if (messageBuffer.length() > 5000) {
            messageBuffer.setLength(5000);
        }
        
        tvPsdkMessage.setText(messageBuffer.toString());
    }
    
    private void clearMessages() {
        messageBuffer.setLength(0);
        tvPsdkMessage.setText(R.string.uxsdk_payload_no_data);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (widgetModel != null) {
            widgetModel.cleanup();
        }
    }
}
```

### 步骤 3: 实现 Payload 通信

#### 3.1 发送数据到 PSDK

```java
public void sendDataToPsdk(byte[] data) {
    djiSdkModel.setValue(
        KeyTools.createKey(PayloadKey.KeySendDataToPsdk, currentPayloadIndex),
        data
    ).subscribe(
        () -> {
            // 发送成功
            payloadMessageProcessor.onNext("Data sent successfully");
        },
        error -> {
            // 发送失败
            payloadMessageProcessor.onNext("Failed to send data: " + error.getMessage());
        }
    );
}
```

#### 3.2 接收 PSDK 数据

```java
private void setupDataReceiver() {
    bindDataProcessor(
        KeyTools.createKey(PayloadKey.KeyReceiveDataFromPsdk, currentPayloadIndex),
        payloadMessageProcessor,
        data -> {
            // 处理接收到的数据
            if (data != null) {
                return "Received: " + bytesToHex(data);
            }
            return "";
        }
    );
}

private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
        sb.append(String.format("%02X ", b));
    }
    return sb.toString();
}
```

### 步骤 4: 添加错误处理

```java
private void handleConnectionError(Throwable error) {
    String errorMessage = "Connection error: " + error.getMessage();
    payloadMessageProcessor.onNext(errorMessage);
    connectionStateProcessor.onNext(false);
}

private void setupConnectionTimeout() {
    // 设置连接超时（例如 10 秒）
    compositeDisposable.add(
        Flowable.timer(10, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(aLong -> {
                if (!isConnected) {
                    handleConnectionError(new TimeoutException("Connection timeout"));
                }
            })
    );
}
```

## 🔑 关键 API 参考

### Payload Key 列表

```java
// 连接状态
PayloadKey.KeyConnection

// 发送数据到 PSDK
PayloadKey.KeySendDataToPsdk

// 从 PSDK 接收数据
PayloadKey.KeyReceiveDataFromPsdk

// Payload 产品名称
PayloadKey.KeyPayloadProductName

// Payload 固件版本
PayloadKey.KeyFirmwareVersion

// Payload 相机类型
PayloadKey.KeyPayloadCameraType
```

### ComponentIndexType

```java
ComponentIndexType.LEFT_OR_MAIN  // PSDK 1
ComponentIndexType.RIGHT         // PSDK 2
ComponentIndexType.UP            // PSDK 3
```

## 🧪 测试建议

### 单元测试

创建 `PayloadWidgetModelTest.java`：

```java
@Test
public void testPayloadConnection() {
    // 测试连接逻辑
    widgetModel.connectPayload();
    
    // 验证连接状态
    widgetModel.getConnectionState()
        .test()
        .assertValue(true);
}

@Test
public void testPayloadIndexSwitch() {
    // 测试切换 Payload
    widgetModel.setPayloadIndex(1);
    
    // 验证索引更新
    assertEquals(ComponentIndexType.RIGHT, widgetModel.getCurrentPayloadIndex());
}
```

### 集成测试

1. 连接真实的 PSDK 设备
2. 测试数据发送和接收
3. 测试连接超时处理
4. 测试错误恢复机制

## 📊 性能优化

### 1. 数据缓冲

```java
private final Queue<String> messageQueue = new LinkedList<>();
private static final int MAX_QUEUE_SIZE = 100;

private void addMessageToQueue(String message) {
    if (messageQueue.size() >= MAX_QUEUE_SIZE) {
        messageQueue.poll();
    }
    messageQueue.offer(message);
}
```

### 2. 线程管理

```java
// 使用后台线程处理数据
.subscribeOn(Schedulers.io())
.observeOn(AndroidSchedulers.mainThread())
```

### 3. 内存管理

```java
@Override
public void onDestroyView() {
    super.onDestroyView();
    // 清理资源
    compositeDisposable.clear();
    messageBuffer.setLength(0);
    messageQueue.clear();
}
```

## 🐛 常见问题

### Q: 如何处理多个 PSDK 同时连接？

A: 使用 Map 管理多个连接：

```java
private Map<ComponentIndexType, Boolean> connectionStates = new HashMap<>();
```

### Q: 如何实现自动重连？

A: 使用 RxJava 的 retry 操作符：

```java
.retryWhen(errors -> errors
    .zipWith(Flowable.range(1, 3), (error, retryCount) -> retryCount)
    .flatMap(retryCount -> Flowable.timer(retryCount * 2, TimeUnit.SECONDS))
)
```

### Q: 如何处理大量数据？

A: 使用分页或流式处理：

```java
.buffer(100, TimeUnit.MILLISECONDS)
.filter(list -> !list.isEmpty())
```

## 📚 参考资料

- [DJI Mobile SDK Documentation](https://developer.dji.com/mobile-sdk/)
- [Payload SDK Documentation](https://developer.dji.com/payload-sdk/)
- [RxJava Documentation](https://github.com/ReactiveX/RxJava)

## ✅ 开发检查清单

- [ ] 创建 PayloadWidgetModel
- [ ] 实现连接/断开逻辑
- [ ] 实现数据发送功能
- [ ] 实现数据接收功能
- [ ] 添加错误处理
- [ ] 添加连接超时处理
- [ ] 实现自动重连
- [ ] 编写单元测试
- [ ] 进行集成测试
- [ ] 性能优化
- [ ] 代码审查
- [ ] 文档更新

---

**版本**: 1.0.0  
**更新日期**: 2024
