package com.simplemap.update

import com.simplemap.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class AppUpdateInfo(
    val latestVersionName: String,
    val releasePageUrl: String,
    val updateAvailable: Boolean,
)

interface AppUpdateRepository {
    fun checkForUpdate(currentVersionName: String): Result<AppUpdateInfo>
}

class GitHubReleaseUpdateRepository : AppUpdateRepository {
    override fun checkForUpdate(currentVersionName: String): Result<AppUpdateInfo> = runCatching {
        val connection = URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "SimpleMap/${BuildConfig.VERSION_NAME}")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("GitHub Release 返回 HTTP $responseCode")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(payload)
            val tagName = release.getString("tag_name").trim()
            val releasePageUrl = release.getString("html_url").trim()
            require(releasePageUrl.startsWith(RELEASE_PAGE_URL_PREFIX)) {
                "GitHub Release 地址无效"
            }
            AppUpdateInfo(
                latestVersionName = tagName.removePrefix("v").removePrefix("V"),
                releasePageUrl = releasePageUrl,
                updateAvailable = isNewerReleaseVersion(tagName, currentVersionName),
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/qaz6750/SimpleMap/releases/latest"
        const val RELEASE_PAGE_URL_PREFIX =
            "https://github.com/qaz6750/SimpleMap/releases/"
    }
}

internal fun isNewerReleaseVersion(latestVersion: String, currentVersion: String): Boolean {
    val latest = ParsedReleaseVersion.parse(latestVersion) ?: return false
    val current = ParsedReleaseVersion.parse(currentVersion) ?: return false
    return latest.compareTo(current) > 0
}

private data class ParsedReleaseVersion(
    val numbers: List<Long>,
    val preRelease: List<String>?,
) : Comparable<ParsedReleaseVersion> {
    override fun compareTo(other: ParsedReleaseVersion): Int {
        repeat(maxOf(numbers.size, other.numbers.size)) { index ->
            val comparison = numbers.getOrElse(index) { 0L }
                .compareTo(other.numbers.getOrElse(index) { 0L })
            if (comparison != 0) return comparison
        }
        if (preRelease == null && other.preRelease == null) return 0
        if (preRelease == null) return 1
        if (other.preRelease == null) return -1
        repeat(maxOf(preRelease.size, other.preRelease.size)) { index ->
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private val pattern = Regex(
            "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
        )

        fun parse(value: String): ParsedReleaseVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
            val preRelease = match.groupValues[2].takeIf(String::isNotEmpty)?.split('.')
            return ParsedReleaseVersion(numbers, preRelease)
        }
    }
}