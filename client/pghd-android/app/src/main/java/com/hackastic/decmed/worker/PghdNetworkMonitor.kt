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
                        if (hasValidatedInternet(connectivityManager, network)) {
                            DecmedLog.i(TAG, "Validated internet became available; scheduling PGHD submit.")
                            PghdWorkScheduler.scheduleSubmitWhenConnected(appContext)
                        }
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        if (
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        ) {
                            DecmedLog.i(TAG, "Network validated; scheduling PGHD submit.")
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

    private fun hasValidatedInternet(
        connectivityManager: ConnectivityManager,
        network: Network
    ): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
