package com.simplemap.offline

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.amap.api.maps.offlinemap.OfflineMapManager
import com.amap.api.maps.offlinemap.OfflineMapStatus

enum class OfflineDownloadState {
    NotDownloaded,
    Waiting,
    Downloading,
    Paused,
    Installed,
    UpdateAvailable,
    Failed,
}

data class OfflineCity(
    val code: String,
    val name: String,
    val sizeBytes: Long,
    val progress: Int,
    val state: OfflineDownloadState,
)

fun downloadedOfflineBytes(cities: List<OfflineCity>): Long = cities.sumOf { city ->
    val sizeBytes = city.sizeBytes.coerceAtLeast(0L)
    when (city.state) {
        OfflineDownloadState.Installed,
        OfflineDownloadState.UpdateAvailable,
        -> sizeBytes
        OfflineDownloadState.Waiting,
        OfflineDownloadState.Downloading,
        OfflineDownloadState.Paused,
        -> sizeBytes * city.progress.coerceIn(0, 100) / 100L
        OfflineDownloadState.NotDownloaded,
        OfflineDownloadState.Failed,
        -> 0L
    }
}

interface OfflineMapRepository {
    fun loadCities(): Result<List<OfflineCity>>
    fun download(cityName: String): Result<Unit>
    fun pause(cityName: String)
    fun remove(cityName: String)
    fun setOnChanged(listener: (OfflineCity) -> Unit)
    fun destroy()
}

class AmapOfflineMapRepository(context: Context) : OfflineMapRepository {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onChanged: (OfflineCity) -> Unit = {}
    private val manager = OfflineMapManager(
        context.applicationContext,
        object : OfflineMapManager.OfflineMapDownloadListener {
            override fun onDownload(status: Int, completeCode: Int, cityName: String) {
                dispatchChanged(cityName)
            }

            override fun onCheckUpdate(hasNew: Boolean, cityName: String) {
                dispatchChanged(cityName)
            }

            override fun onRemove(success: Boolean, cityName: String, description: String) {
                dispatchChanged(cityName)
            }
        },
    )

    override fun loadCities(): Result<List<OfflineCity>> = runCatching {
        manager.offlineMapCityList
            .map { city ->
                OfflineCity(
                    code = city.code.orEmpty(),
                    name = city.city.orEmpty(),
                    sizeBytes = city.size,
                    progress = city.getcompleteCode(),
                    state = city.state.toDownloadState(),
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedWith(compareByDescending<OfflineCity> { it.state == OfflineDownloadState.Installed }.thenBy { it.name })
    }

    override fun download(cityName: String): Result<Unit> = runCatching {
        manager.downloadByCityName(cityName)
    }

    override fun pause(cityName: String) = manager.pauseByName(cityName)

    override fun remove(cityName: String) = manager.remove(cityName)

    override fun setOnChanged(listener: (OfflineCity) -> Unit) {
        onChanged = listener
    }

    override fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        manager.destroy()
    }

    private fun dispatchChanged(cityName: String) {
        val city = managerItem(cityName) ?: return
        mainHandler.post { onChanged(city) }
    }

    private fun managerItem(cityName: String): OfflineCity? = manager.getItemByCityName(cityName)?.let {
        OfflineCity(
            code = it.code.orEmpty(),
            name = it.city.orEmpty(),
            sizeBytes = it.size,
            progress = it.getcompleteCode(),
            state = it.state.toDownloadState(),
        )
    }

    private fun Int.toDownloadState() = when (this) {
        OfflineMapStatus.WAITING -> OfflineDownloadState.Waiting
        OfflineMapStatus.LOADING, OfflineMapStatus.UNZIP -> OfflineDownloadState.Downloading
        OfflineMapStatus.PAUSE, OfflineMapStatus.STOP -> OfflineDownloadState.Paused
        OfflineMapStatus.SUCCESS -> OfflineDownloadState.Installed
        OfflineMapStatus.NEW_VERSION -> OfflineDownloadState.UpdateAvailable
        OfflineMapStatus.ERROR,
        OfflineMapStatus.EXCEPTION_AMAP,
        OfflineMapStatus.EXCEPTION_NETWORK_LOADING,
        OfflineMapStatus.EXCEPTION_SDCARD,
        OfflineMapStatus.START_DOWNLOAD_FAILED,
        -> OfflineDownloadState.Failed
        else -> OfflineDownloadState.NotDownloaded
    }
}