package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.databinding.FragmentUsbSendDataBinding
import dji.sampleV5.aircraft.models.USBSendDataVM
import dji.sampleV5.aircraft.data.source.USBDataRepository

/**
 * USB数据发送界面 - 简化版
 * 安卓设备作为USB从设备，不需要扫描和连接设备
 * 数据存入缓冲区，由PC端主动轮询读取
 * 
 * @author USB开发
 * @date 2024/1/1
 * 
 * Copyright (c) 2024, DJI All Rights Reserved.
 */
class USBSendDataFragment : DJIFragment() {
    private val viewModel: USBSendDataVM by viewModels()
    private var _binding: FragmentUsbSendDataBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentUsbSendDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化USB数据仓库
        val usbRepository = USBDataRepository(requireContext())
        viewModel.initRepository(usbRepository)
        
        setupUI()
        observeViewModel()
        
        // 检查USB连接状态
        viewModel.checkUSBConnection()
    }

    private fun setupUI() {
        // 数据发送按钮
        binding.btnSendData.setOnClickListener {
            val data = binding.etData.text.toString()
            if (data.isNotEmpty()) {
                viewModel.sendData(data)
                // 清空输入框
                binding.etData.text?.clear()
            }
        }
        
        // 清空历史记录按钮
        binding.btnClearHistory.setOnClickListener {
            viewModel.clearHistory()
        }
        
        // 协议选择（简化版暂时隐藏，因为USB模式不需要选择协议）
        binding.spinnerProtocol.visibility = View.GONE
    }
    
    private fun updateUIForProtocol() {
        // USB模式：显示USB连接状态
        binding.tvConnectionStatus.text = viewModel.getUSBConnectionStatus()
        
        // 更新发送按钮状态
        updateSendButtonState()
    }
    
    private fun updateSendButtonState() {
        // USB模式下，发送按钮始终可用（数据会存入缓冲区）
        binding.btnSendData.isEnabled = true
        binding.btnSendData.setTextColor(android.graphics.Color.WHITE)
    }

    private fun observeViewModel() {
        // 观察连接状态
        viewModel.usbConnectionState.observe(viewLifecycleOwner) { state ->
            updateConnectionUI(state)
        }
        
        // 观察缓冲区大小
        viewModel.bufferSize.observe(viewLifecycleOwner) { size ->
            binding.tvBufferSize.text = "缓冲区大小: $size"
        }
        
        // 观察发送历史记录
        viewModel.sendHistory.observe(viewLifecycleOwner) { history ->
            if (history.isNotEmpty()) {
                binding.tvHistory.text = history.joinToString("\n")
            } else {
                binding.tvHistory.text = "暂无发送记录"
            }
        }
    }

    private fun updateConnectionUI(state: dji.sampleV5.aircraft.data.USBConnectionState) {
        when (state) {
            dji.sampleV5.aircraft.data.USBConnectionState.DISCONNECTED -> {
                binding.tvConnectionStatus.text = "USB未连接 - 请通过USB连接PC"
                binding.btnSendData.isEnabled = true // 即使未连接也可以输入数据
            }
            dji.sampleV5.aircraft.data.USBConnectionState.CONNECTING -> {
                binding.tvConnectionStatus.text = "连接中..."
            }
            dji.sampleV5.aircraft.data.USBConnectionState.CONNECTED -> {
                binding.tvConnectionStatus.text = "USB已连接 - 等待PC轮询"
                binding.btnSendData.isEnabled = true
            }
            dji.sampleV5.aircraft.data.USBConnectionState.ERROR -> {
                binding.tvConnectionStatus.text = "连接错误"
                binding.btnSendData.isEnabled = true // 即使错误也可以输入数据
            }
            else -> {
                binding.tvConnectionStatus.text = viewModel.getUSBConnectionStatus()
                binding.btnSendData.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}