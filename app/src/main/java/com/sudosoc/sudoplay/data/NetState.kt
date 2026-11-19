package com.sudosoc.sudoplay.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Is there an internet connection right now? Drives the "offline" banner. */
object NetState {
    var online by mutableStateOf(true); private set
    private var started = false

    fun start(ctx: Context) {
        if (started) return
        started = true
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun check() { online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
        check()
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { online = true }
                override fun onLost(network: Network) { check() }
                override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) { online = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
            })
        }
    }
}
