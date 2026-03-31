package dji.sampleV5.aircraft.pages

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.data.USBConnectionState
import dji.sampleV5.aircraft.data.source.USBDataRepository
import dji.sampleV5.aircraft.databinding.FragmentUsbSendDataBinding
import dji.sampleV5.aircraft.models.USBSendDataVM

class USBSendDataFragment : DJIFragment() {
    private val viewModel: USBSendDataVM by viewModels()
    private var _binding: FragmentUsbSendDataBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsbSendDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initRepository(USBDataRepository(requireContext()))
        setupUI()
        observeViewModel()
        viewModel.checkUSBConnection()
    }

    private fun setupUI() {
        binding.btnSendData.setOnClickListener {
            val data = binding.etData.text.toString().trim()
            if (data.isNotEmpty()) {
                viewModel.addTestData(data)
                binding.etData.text?.clear()
            }
        }

        binding.btnTogglePsdk.setOnClickListener {
            viewModel.togglePsdkListening()
        }

        binding.btnRefreshStatus.setOnClickListener {
            viewModel.checkUSBConnection()
        }

        binding.btnClearHistory.setOnClickListener {
            viewModel.clearHistory()
        }

        binding.spinnerProtocol.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.usbConnectionState.observe(viewLifecycleOwner) { state ->
            updateConnectionUI(state)
        }

        viewModel.bufferSize.observe(viewLifecycleOwner) { size ->
            binding.tvBufferSize.text = "缓冲区大小: $size"
        }

        viewModel.sendHistory.observe(viewLifecycleOwner) { history ->
            binding.tvHistory.text = if (history.isEmpty()) {
                "暂无发送记录"
            } else {
                history.joinToString("\n")
            }
        }

        viewModel.pollingHint.observe(viewLifecycleOwner) { hint ->
            binding.tvPollingHint.text = hint
        }

        viewModel.psdkListening.observe(viewLifecycleOwner) { listening ->
            binding.tvPsdkStatus.text = if (listening) {
                "PSDK 监听: 已开启，收到数据后自动入缓冲区"
            } else {
                "PSDK 监听: 未开启"
            }
            binding.btnTogglePsdk.text = if (listening) "停止监听 PSDK" else "开启监听 PSDK"
        }

        viewModel.latestPacketPreview.observe(viewLifecycleOwner) { preview ->
            binding.tvLatestPacket.text = preview
        }
    }

    private fun updateConnectionUI(state: USBConnectionState) {
        when (state) {
            USBConnectionState.DISCONNECTED -> {
                binding.tvConnectionStatus.text = "轮询服务未启动"
                binding.tvConnectionStatus.setTextColor(Color.RED)
            }
            USBConnectionState.SCANNING -> {
                binding.tvConnectionStatus.text = "正在扫描 USB 状态..."
                binding.tvConnectionStatus.setTextColor(Color.DKGRAY)
            }
            USBConnectionState.CONNECTING -> {
                binding.tvConnectionStatus.text = "轮询服务启动中..."
                binding.tvConnectionStatus.setTextColor(Color.DKGRAY)
            }
            USBConnectionState.CONNECTED -> {
                binding.tvConnectionStatus.text = viewModel.getUSBConnectionStatus()
                binding.tvConnectionStatus.setTextColor(Color.parseColor("#0B7A16"))
            }
            USBConnectionState.ERROR -> {
                binding.tvConnectionStatus.text = "轮询服务启动失败"
                binding.tvConnectionStatus.setTextColor(Color.RED)
            }
        }
        binding.btnSendData.isEnabled = true
        binding.btnSendData.setTextColor(Color.WHITE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
