package com.massspx.droidbridge

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build

class WifiDirectController(private val activity: Activity) {
    private val manager = activity.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(activity, activity.mainLooper, null)
    private var receiver: BroadcastReceiver? = null
    var onPeers: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnection: ((WifiP2pInfo) -> Unit)? = null

    fun register() {
        if (receiver != null || manager == null || channel == null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        val localReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION ->
                        manager.requestPeers(channel) { list -> onPeers?.invoke(list.deviceList.toList()) }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION ->
                        manager.requestConnectionInfo(channel) { info -> onConnection?.invoke(info) }
                }
            }
        }
        receiver = localReceiver
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(localReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(localReceiver, filter)
        }
    }

    fun createGroup(callback: (String) -> Unit) {
        val m = manager
        val c = channel
        if (m == null || c == null) { callback("ဒီဖုန်းမှာ Wi-Fi Direct မရနိုင်ပါ"); return }
        m.createGroup(c, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { callback("Wi-Fi Direct group ဖန်တီးပြီးပါပြီ။ ဒီဖုန်းကို Receiver အဖြစ်ထားပါ။") }
            override fun onFailure(reason: Int) { callback("Group ဖန်တီးမရပါ: $reason") }
        })
    }

    fun discoverPeers(callback: (String) -> Unit) {
        val m = manager
        val c = channel
        if (m == null || c == null) { callback("ဒီဖုန်းမှာ Wi-Fi Direct မရနိုင်ပါ"); return }
        m.discoverPeers(c, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { callback("အနီးအနား Wi-Fi Direct device များကို ရှာနေသည်…") }
            override fun onFailure(reason: Int) { callback("Device ရှာမရပါ: $reason • Location/Wi-Fi permission ကိုစစ်ပါ") }
        })
    }

    fun connect(device: WifiP2pDevice, callback: (String) -> Unit) {
        val m = manager
        val c = channel
        if (m == null || c == null) { callback("Wi-Fi Direct မရနိုင်ပါ"); return }
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        m.connect(c, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { callback("Connecting to ${device.deviceName}…") }
            override fun onFailure(reason: Int) { callback("Connection failed: $reason") }
        })
    }

    fun removeGroup(callback: (String) -> Unit) {
        val m = manager
        val c = channel
        if (m == null || c == null) { callback("Wi-Fi Direct မရနိုင်ပါ"); return }
        m.removeGroup(c, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { callback("Wi-Fi Direct group disconnected") }
            override fun onFailure(reason: Int) { callback("Disconnect failed: $reason") }
        })
    }

    fun unregister() {
        receiver?.let {
            try { activity.unregisterReceiver(it) } catch (_: Exception) { }
        }
        receiver = null
    }
}
