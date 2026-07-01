package com.hackastic.decmed.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.hackastic.decmed.utils.DecmedLog

object PghdNetworkMonitor {
    private const val TAG = "PghdNetworkMonitor"
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        DecmedLog.i(TAG, "Network became available; scheduling PGHD submit.")
                        PghdWorkScheduler.scheduleSubmitWhenConnected(appContext)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            DecmedLog.i(TAG, "Internet-capable network changed; scheduling PGHD submit.")
                            PghdWorkScheduler.scheduleSubmitWhenConnected(appContext)
                        }
                    }
                }
            )
            registered = true
        }.onFailure { err ->
            DecmedLog.e(TAG, "Unable to register PGHD network monitor", err)
        }
    }

}
