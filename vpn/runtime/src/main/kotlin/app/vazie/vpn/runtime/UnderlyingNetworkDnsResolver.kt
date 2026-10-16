package app.vazie.vpn.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Resolves only the VPN server bootstrap name, before Xray starts carrying traffic. */
internal class UnderlyingNetworkDnsResolver(
    context: Context,
) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun resolveIpv4(hostname: String): String? {
        val manager = connectivity ?: return null
        val network = manager.usableUnderlyingNetwork() ?: return null
        val encodedName = URLEncoder.encode(hostname, Charsets.UTF_8.name())
        val url = URL("$DOH_ENDPOINT?name=$encodedName&type=A")
        val connection = runCatching { network.openConnection(url) as HttpsURLConnection }
            .getOrNull() ?: return null

        return try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/dns-json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_RESPONSE_BYTES) return null
            parseIpv4Response(connection.readBoundedBody())
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun ConnectivityManager.usableUnderlyingNetwork(): Network? = allNetworks.firstOrNull { network ->
        val capabilities = runCatching { getNetworkCapabilities(network) }.getOrNull()
            ?: return@firstOrNull false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun HttpsURLConnection.readBoundedBody(): String {
        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val body = StringBuilder()
            val buffer = CharArray(2_048)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                check(body.length + read <= MAX_RESPONSE_BYTES) { "DoH response is too large" }
                body.append(buffer, 0, read)
            }
            return body.toString()
        }
    }

    internal companion object {
        private const val DOH_ENDPOINT = "https://cloudflare-dns.com/dns-query"
        private const val TIMEOUT_MILLIS = 5_000
        private const val MAX_RESPONSE_BYTES = 64 * 1_024
        private val IPV4_OCTET = Regex("0|[1-9][0-9]{0,2}")

        fun parseIpv4Response(body: String): String? {
            val document = runCatching { JSONObject(body) }.getOrNull() ?: return null
            if (document.optInt("Status", -1) != 0) return null
            val answers = document.optJSONArray("Answer") ?: return null
            for (index in 0 until answers.length()) {
                val answer = answers.optJSONObject(index) ?: continue
                if (answer.optInt("type", -1) != 1) continue
                val address = answer.optString("data")
                if (address.isIpv4Literal()) return address
            }
            return null
        }

        private fun String.isIpv4Literal(): Boolean {
            val octets = split('.')
            return octets.size == 4 && octets.all { octet ->
                IPV4_OCTET.matches(octet) && octet.toInt() in 0..255
            }
        }
    }
}
