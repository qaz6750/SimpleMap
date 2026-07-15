package com.simplemap.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class NetworkStatus(
    val available: Boolean,
    val connectedViaWifi: Boolean,
)

fun canDownloadOfflineMap(status: NetworkStatus, wifiOnly: Boolean): Boolean =
    status.available && (!wifiOnly || status.connectedViaWifi)

@Composable
fun rememberNetworkStatus(): NetworkStatus {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var status by remember(connectivityManager) {
        mutableStateOf(connectivityManager.currentNetworkStatus())
    }

    DisposableEffect(connectivityManager) {
        val mainHandler = Handler(Looper.getMainLooper())
        fun refresh() {
            mainHandler.post { status = connectivityManager.currentNetworkStatus() }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refresh()
            }

            override fun onLost(network: Network) {
                refresh()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                refresh()
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    return status
}

private fun ConnectivityManager.currentNetworkStatus(): NetworkStatus {
    val network = activeNetwork ?: return NetworkStatus(available = false, connectedViaWifi = false)
    val capabilities = getNetworkCapabilities(network)
        ?: return NetworkStatus(available = false, connectedViaWifi = false)
    return NetworkStatus(
        available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        connectedViaWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
    )
}