package com.symphogear.tv.extra

import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.*

class TLSSocketFactory : SSLSocketFactory() {
    private val factory: SSLSocketFactory

    fun trustAllHttps(): TLSSocketFactory {
        try {
            HttpsURLConnection.setDefaultHostnameVerifier { _: String?, _: SSLSession? -> true }
            HttpsURLConnection.setDefaultSSLSocketFactory(factory)
        } catch (e: Exception) {
            Log.e("TLSSocketFactory", "trustAllHttps failed", e)
        }
        return this
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return factory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return factory.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(): Socket {
        return enableTLSOnSocket(factory.createSocket())
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return enableTLSOnSocket(factory.createSocket(s, host, port, autoClose))
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return enableTLSOnSocket(factory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        return enableTLSOnSocket(factory.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return enableTLSOnSocket(factory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        return enableTLSOnSocket(factory.createSocket(address, port, localAddress, localPort))
    }

    private fun enableTLSOnSocket(socket: Socket): Socket {
        if (socket is SSLSocket) {
            try {
                val supported = socket.supportedProtocols
                val enabled = mutableListOf<String>()
                if (supported.contains("TLSv1.2")) enabled.add("TLSv1.2")
                if (supported.contains("TLSv1.1")) enabled.add("TLSv1.1")
                if (supported.contains("TLSv1")) enabled.add("TLSv1")
                if (enabled.isNotEmpty()) socket.enabledProtocols = enabled.toTypedArray()
            } catch (e: Exception) {
                Log.e("TLSSocketFactory", "enableTLSOnSocket failed", e)
            }
        }
        return socket
    }

    companion object {
        var trustManagers: Array<TrustManager>? = null
    }

    init {
        if (trustManagers == null) {
            trustManagers = arrayOf(HttpsTrustManager())
        }

        var tempFactory: SSLSocketFactory? = null

        // Try TLSv1.2 first for Android < 22, then fall back
        val protocols = if (Build.VERSION.SDK_INT < 22)
            listOf("TLSv1.2", "TLSv1.1", "TLS")
        else
            listOf("TLS")

        for (protocol in protocols) {
            try {
                val ctx = SSLContext.getInstance(protocol)
                ctx.init(null, trustManagers, SecureRandom())
                tempFactory = ctx.socketFactory
                break
            } catch (e: Exception) {
                Log.e("TLSSocketFactory", "SSLContext $protocol failed", e)
            }
        }

        // Last resort fallback to default
        factory = tempFactory ?: SSLSocketFactory.getDefault() as SSLSocketFactory
    }
}