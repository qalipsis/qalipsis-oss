package io.evolue.plugins.netty.configuration

data class TlsConfiguration internal constructor(
    var disableCertificateVerification: Boolean = false
) {
    internal var protocols = arrayOf<String>()

    fun protocols(vararg protocols: String) {
        this.protocols += protocols
    }
}