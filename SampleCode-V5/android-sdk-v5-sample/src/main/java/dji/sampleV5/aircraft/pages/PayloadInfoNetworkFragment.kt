package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.databinding.FragPayloadInfoNetworkBinding
import dji.sampleV5.aircraft.models.PayloadInfoNetworkVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.v5.manager.aircraft.payload.PayloadIndexType
import dji.v5.utils.common.LogUtils

/**
 * Payload设备信息与网络发送界面
 * 
 * @author Payload开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class PayloadInfoNetworkFragment : DJIFragment() {
    
    private val viewModel: PayloadInfoNetworkVM by viewModels()
    private var _binding: FragPayloadInfoNetworkBinding? = null
    private val binding get() = _binding!!
    
    companion object {
        const val TAG = "PayloadInfoNetworkFragment"
        const val KEY_PAYLOAD_INDEX_TYPE = "payload_index_type"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragPayloadInfoNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
        initPayloadListener()
        
        // 设置默认服务器地址
        binding.etServerIp.setText(viewModel.getCurrentServerHost())
        binding.etServerPort.setText(viewModel.getCurrentServerPort().toString())
    }
    
    private fun setupUI() {
        // 服务器连接按钮
        binding.btnConnectServer.setOnClickListener {
            val host = binding.etServerIp.text.toString().trim()
            val portText = binding.etServerPort.text.toString().trim()
            
            if (host.isEmpty() || portText.isEmpty()) {
                ToastUtils.showToast("请输入服务器地址和端口")
                return@setOnClickListener
            }
            
            try {
                val port = portText.toInt()
                viewModel.setServerAddress(host, port)
                viewModel.connectToServer()
            } catch (e: NumberFormatException) {
                ToastUtils.showToast("端口号格式错误")
            }
        }
        
        // 断开连接按钮
        binding.btnDisconnectServer.setOnClickListener {
            viewModel.disconnectFromServer()
        }
        
        // 设置发送模式选择器
        setupSendModeSpinner()
        
        // 设置数据格式选择器
        setupDataFormatSpinner()
        
        // 发送数据到网络按钮
        binding.btnSendData.setOnClickListener {
            val data = binding.etSendData.text.toString().trim()
            
            // 检查网络连接状态
            if (viewModel.networkConnectionState.value != dji.sampleV5.aircraft.data.NetworkConnectionState.CONNECTED) {
                ToastUtils.showToast("网络未连接，无法发送数据")
                return@setOnClickListener
            }
            
            // 根据发送模式决定是否检查数据为空
            when (viewModel.dataSendMode.value) {
                dji.sampleV5.aircraft.data.DataSendMode.MANUAL_ONLY, 
                dji.sampleV5.aircraft.data.DataSendMode.BOTH -> {
                    if (data.isEmpty()) {
                        ToastUtils.showToast("请输入要发送的数据")
                        return@setOnClickListener
                    }
                }
                dji.sampleV5.aircraft.data.DataSendMode.AUTO_ONLY -> {
                    // 自动模式不需要手动数据
                }
                else -> {
                    if (data.isEmpty()) {
                        ToastUtils.showToast("请输入要发送的数据")
                        return@setOnClickListener
                    }
                }
            }
            
            viewModel.sendManualData(data)
        }
        
        // 监听网络数据输入框变化，实时更新发送按钮状态
        binding.etSendData.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSendButtonState()
            }
        })
        
        // 发送数据到PSDK按钮
        binding.btnSendPsdkData.setOnClickListener {
            val data = binding.etSendPsdkData.text.toString().trim()
            
            if (data.isEmpty()) {
                ToastUtils.showToast("请输入要发送到PSDK的数据")
                return@setOnClickListener
            }
            
            viewModel.sendDataToPSDK(data)
        }
    }
    
    private fun setupSendModeSpinner() {
        val sendModes = listOf(
            "仅手动发送" to dji.sampleV5.aircraft.data.DataSendMode.MANUAL_ONLY,
            "仅自动发送PSDK" to dji.sampleV5.aircraft.data.DataSendMode.AUTO_ONLY,
            "手动+自动发送" to dji.sampleV5.aircraft.data.DataSendMode.BOTH
        )
        
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sendModes.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSendMode.adapter = adapter
        
        binding.spinnerSendMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedMode = sendModes[position].second
                viewModel.setDataSendMode(selectedMode)
                
                // 根据模式更新界面
                updateUIForSendMode(selectedMode)
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }
    
    private fun setupDataFormatSpinner() {
        val formatModes = listOf(
            "原始数据包" to dji.sampleV5.aircraft.data.DataFormatMode.RAW_DATA_ONLY,
            "PSDK数据格式" to dji.sampleV5.aircraft.data.DataFormatMode.PSDK_FORMAT,
            "混合数据格式" to dji.sampleV5.aircraft.data.DataFormatMode.MIXED_FORMAT
        )
        
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            formatModes.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDataFormat.adapter = adapter
        
        binding.spinnerDataFormat.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedMode = formatModes[position].second
                viewModel.setDataFormatMode(selectedMode)
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }
    
    private fun updateUIForSendMode(mode: dji.sampleV5.aircraft.data.DataSendMode) {
        when (mode) {
            dji.sampleV5.aircraft.data.DataSendMode.MANUAL_ONLY -> {
                binding.etSendData.hint = "请输入要发送的数据..."
                binding.etSendData.isEnabled = true
            }
            dji.sampleV5.aircraft.data.DataSendMode.AUTO_ONLY -> {
                binding.etSendData.hint = "自动模式：仅发送PSDK数据"
                binding.etSendData.isEnabled = false
                binding.etSendData.text.clear()
            }
            dji.sampleV5.aircraft.data.DataSendMode.BOTH -> {
                binding.etSendData.hint = "请输入要发送的数据（将与PSDK数据一起发送）"
                binding.etSendData.isEnabled = true
            }
        }
        
        // 更新发送按钮状态
        updateSendButtonState()
    }
    
    private fun observeViewModel() {
        // 观察Payload基本信息
        viewModel.payloadBasicInfo.observe(viewLifecycleOwner) { info ->
            updatePayloadInfoUI(info)
        }
        
        // 观察网络连接状态
        viewModel.networkConnectionState.observe(viewLifecycleOwner) { state ->
            updateNetworkConnectionUI(state)
            updateSendButtonState()
        }
        
        // 观察数据发送进度
        viewModel.sendProgress.observe(viewLifecycleOwner) { progress ->
            updateSendProgressUI(progress)
        }
        
        // 观察数据发送模式
        viewModel.dataSendMode.observe(viewLifecycleOwner) { mode ->
            updateUIForSendMode(mode)
        }
        
        // 观察数据格式模式
        viewModel.dataFormatMode.observe(viewLifecycleOwner) { mode ->
            // 可以在这里添加格式模式变化的处理逻辑
        }
        
        // 观察PSDK接收数据
        viewModel.psdkReceivedData.observe(viewLifecycleOwner) { data ->
            binding.tvPsdkReceivedData.text = data.toString()
            
            // 自动滚动到最新数据
            binding.tvPsdkReceivedData.post {
                val scrollView = binding.tvPsdkReceivedData.parent as? android.widget.ScrollView
                scrollView?.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
        
        // 观察日志消息
        viewModel.logMessages.observe(viewLifecycleOwner) { log ->
            binding.tvLog.text = log.toString()
            
            // 自动滚动到最新日志
            binding.tvLog.post {
                val scrollView = binding.tvLog.parent as? android.widget.ScrollView
                scrollView?.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
    
    private fun initPayloadListener() {
        arguments?.run {
            val payloadIndexType = PayloadIndexType.find(
                getInt(KEY_PAYLOAD_INDEX_TYPE, PayloadIndexType.UP.value())
            )
            viewModel.initPayloadListener(payloadIndexType)
            
            // 更新标题
            binding.tvTitle.text = "${payloadIndexType.name} Payload设备信息与网络发送"
        }
    }
    
    private fun updatePayloadInfoUI(info: dji.v5.manager.aircraft.payload.data.PayloadBasicInfo?) {
        if (info == null) {
            binding.tvPayloadName.text = "设备名称: 未连接"
            binding.tvPayloadType.text = "设备类型: 未知"
            binding.tvPayloadSerial.text = "序列号: 未知"
            binding.tvPayloadFirmware.text = "固件版本: 未知"
            binding.tvPayloadStatus.text = "连接状态: 未连接"
            return
        }
        
        info.apply {
            binding.tvPayloadName.text = "设备名称: $payloadProductName"
            binding.tvPayloadType.text = "设备类型: $payloadType"
            binding.tvPayloadSerial.text = "序列号: $serialNumber"
            binding.tvPayloadFirmware.text = "固件版本: $firmwareVersion"
            binding.tvPayloadStatus.text = "连接状态: ${if (isConnected) "已连接" else "未连接"}"
        }
    }
    
    private fun updateNetworkConnectionUI(state: dji.sampleV5.aircraft.data.NetworkConnectionState) {
        when (state) {
            dji.sampleV5.aircraft.data.NetworkConnectionState.DISCONNECTED -> {
                binding.tvNetworkStatus.text = "网络状态: 未连接"
                binding.btnConnectServer.isEnabled = true
                binding.btnDisconnectServer.isEnabled = false
                binding.btnSendData.isEnabled = false
                
                // 更新按钮颜色
                binding.btnSendData.setTextColor(android.graphics.Color.GRAY)
            }
            dji.sampleV5.aircraft.data.NetworkConnectionState.CONNECTING -> {
                binding.tvNetworkStatus.text = "网络状态: 连接中..."
                binding.btnConnectServer.isEnabled = false
                binding.btnDisconnectServer.isEnabled = false
                binding.btnSendData.isEnabled = false
            }
            dji.sampleV5.aircraft.data.NetworkConnectionState.CONNECTED -> {
                binding.tvNetworkStatus.text = "网络状态: 已连接"
                binding.btnConnectServer.isEnabled = false
                binding.btnDisconnectServer.isEnabled = true
                binding.btnSendData.isEnabled = true
                
                // 更新按钮颜色
                binding.btnSendData.setTextColor(android.graphics.Color.WHITE)
            }
            dji.sampleV5.aircraft.data.NetworkConnectionState.ERROR -> {
                binding.tvNetworkStatus.text = "网络状态: 连接错误"
                binding.btnConnectServer.isEnabled = true
                binding.btnDisconnectServer.isEnabled = false
                binding.btnSendData.isEnabled = false
                
                binding.btnSendData.setTextColor(android.graphics.Color.GRAY)
            }
        }
    }
    
    private fun updateSendButtonState() {
        val isNetworkConnected = viewModel.networkConnectionState.value == dji.sampleV5.aircraft.data.NetworkConnectionState.CONNECTED
        val sendMode = viewModel.dataSendMode.value
        val hasManualData = binding.etSendData.text.toString().trim().isNotEmpty()
        
        val isEnabled = when (sendMode) {
            dji.sampleV5.aircraft.data.DataSendMode.MANUAL_ONLY -> isNetworkConnected && hasManualData
            dji.sampleV5.aircraft.data.DataSendMode.AUTO_ONLY -> isNetworkConnected
            dji.sampleV5.aircraft.data.DataSendMode.BOTH -> isNetworkConnected && hasManualData
            else -> isNetworkConnected && hasManualData
        }
        
        binding.btnSendData.isEnabled = isEnabled
        
        // 更新按钮文本颜色
        if (isEnabled) {
            binding.btnSendData.setTextColor(android.graphics.Color.WHITE)
        } else {
            binding.btnSendData.setTextColor(android.graphics.Color.GRAY)
        }
    }
    
    private fun updateSendProgressUI(progress: dji.sampleV5.aircraft.data.SendProgress) {
        when (progress) {
            is dji.sampleV5.aircraft.data.SendProgress.IDLE -> {
                binding.progressSend.visibility = View.GONE
                binding.tvSendStatus.text = "发送状态: 等待发送"
            }
            is dji.sampleV5.aircraft.data.SendProgress.STARTED -> {
                binding.progressSend.visibility = View.VISIBLE
                binding.tvSendStatus.text = "发送状态: 开始发送数据..."
            }
            is dji.sampleV5.aircraft.data.SendProgress.PROGRESS -> {
                binding.progressSend.progress = progress.current
                binding.progressSend.max = progress.total
                binding.tvSendStatus.text = "发送进度: ${progress.current}/${progress.total}"
            }
            is dji.sampleV5.aircraft.data.SendProgress.COMPLETED -> {
                binding.progressSend.visibility = View.GONE
                binding.tvSendStatus.text = "发送状态: 发送完成，共发送 ${progress.bytesSent} 字节"
                
                // 仅在手动模式下清空输入框
                if (viewModel.dataSendMode.value != dji.sampleV5.aircraft.data.DataSendMode.AUTO_ONLY) {
                    binding.etSendData.text.clear()
                }
            }
            is dji.sampleV5.aircraft.data.SendProgress.ERROR -> {
                binding.progressSend.visibility = View.GONE
                binding.tvSendStatus.text = "发送状态: 发送失败: ${progress.message ?: "未知错误"}"
            }
        }
        
        // 更新发送按钮状态
        updateSendButtonState()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}