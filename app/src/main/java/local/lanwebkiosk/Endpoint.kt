package local.lanwebkiosk

internal class Endpoint private constructor(val ip: String, val port: Int, val https: Boolean) {
    val url: String
        get() = "${if (https) "https" else "http"}://$ip:$port/"

    companion object {
        private val ipv4Pattern = Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
        private val portPattern = Regex("[0-9]{1,5}")

        fun parse(ip: String, port: String, https: Boolean): Endpoint? {
            val address = ip.trim()
            val portText = port.trim()
            if (!ipv4Pattern.matches(address) || !portPattern.matches(portText)) return null

            val octets = address.split('.').map(String::toInt)
            val portNumber = portText.toInt()
            if (octets.any { it > 255 } || portNumber !in 1..65535) return null

            return Endpoint(octets.joinToString("."), portNumber, https)
        }
    }
}
