package com.simplemap.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberNetworkAvailable(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var available by remember(connectivityManager) {
        mutableStateOf(connectivityManager.isValidatedNetworkAvailable())
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available = connectivityManager.isValidatedNetworkAvailable()
            }

            override fun onLost(network: Network) {
                available = connectivityManager.isValidatedNetworkAvailable()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    return available
}

private fun ConnectivityManager.isValidatedNetworkAvailable(): Boolean {
    val network = activeNetwork ?: return false
    return getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}