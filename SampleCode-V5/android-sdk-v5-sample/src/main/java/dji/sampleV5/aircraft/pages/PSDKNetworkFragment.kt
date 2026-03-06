package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dji.sampleV5.aircraft.databinding.FragPsdkNetworkBinding
import dji.sampleV5.aircraft.models.PayloadInfoNetworkVM

/**
 * PSDK网络功能界面
 * 
 * @author PSDK开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class PSDKNetworkFragment : Fragment() {
    
    private var _binding: FragPsdkNetworkBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: PayloadInfoNetworkVM
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragPsdkNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[PayloadInfoNetworkVM::class.java]
        
        // 设置界面控件
        setupUI()
        
        // 设置观察者
        setupObservers()
    }
    
    private fun setupUI() {
        // 设置发送模式选择器
        setupSendModeSpinner()
        
        // 设置数据格式选择器
        setupDataFormatSpinner()
        
        // 连接网络按钮
        binding.btnConnect.setOnClickListener {
            val host = binding.etServerHost.text.toString().trim()
            val port = binding.etServerPort.text.toString().trim().toIntOrNull() ?: 8888
            
            if (host.isEmpty()) {
                binding.tvConnectionStatus.text = "请输入服务器地址"
                binding.tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                return@setOnClickListener
            }
            
            viewModel.setServerAddress(host, port)
            viewModel.connectToServer()
        }
        
        // 发送数据到网络按钮
        binding.btnSendData.setOnClickListener {
            val data = binding.etSendData.text.toString().trim()
            
            if (data.isEmpty()) {
                // 可以添加Toast提示
                return@setOnClickListener
            }
            
            viewModel.sendManualData(data)
        }
        
        // 发送数据到PSDK按钮
        binding.btnSendPsdkData.setOnClickListener {
            val data = binding.etSendPsdkData.text.toString().trim()
            
            if (data.isEmpty()) {
                // 可以添加Toast提示
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
    
    private fun setupObservers() {
        // 观察网络连接状态
        viewModel.networkConnectionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                dji.sampleV5.aircraft.data.NetworkConnectionState.CONNECTED -> {
                    binding.tvConnectionStatus.text = "已连接"
                    binding.tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    binding.btnConnect.text = "断开连接"
                }
                dji.sampleV5.aircraft.data.NetworkConnectionState.CONNECTING -> {
                    binding.tvConnectionStatus.text = "连接中..."
                    binding.tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                }
                dji.sampleV5.aircraft.data.NetworkConnectionState.DISCONNECTED -> {
                    binding.tvConnectionStatus.text = "未连接"
                    binding.tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    binding.btnConnect.text = "连接网络"
                }
                dji.sampleV5.aircraft.data.NetworkConnectionState.ERROR -> {
                    binding.tvConnectionStatus.text = "连接错误"
                    binding.tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                }
            }
        }
        
        // 观察PSDK接收数据
        viewModel.psdkReceivedData.observe(viewLifecycleOwner) { data ->
            binding.tvPsdkReceivedData.text = data.toString()
        }
        
        // 观察日志消息
        viewModel.logMessages.observe(viewLifecycleOwner) { log ->
            binding.tvLog.text = log.toString()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 可以在这里初始化Payload监听器
        // 需要根据实际情况获取payloadIndexType
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}