/**
 * Pokemon Go Bot  Copyright (C) 2016  PokemonGoBot-authors (see authors.md for more information)
 * This program comes with ABSOLUTELY NO WARRANTY;
 * This is free software, and you are welcome to redistribute it under certain conditions.
 *
 * For more information, refer to the LICENSE file in this repositories root directory
 */

package ink.abb.pogo.scraper

import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass
import com.pokegoapi.api.PokemonGo
import com.pokegoapi.auth.GoogleLogin
import com.pokegoapi.auth.PtcLogin
import ink.abb.pogo.scraper.util.Log
import okhttp3.OkHttpClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.security.cert.CertificateException

/**
 * Allow all certificate to debug with https://github.com/bettse/mitmdump_decoder
 */
fun allowProxy(builder: OkHttpClient.Builder) {
    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("localhost", 8888)))
    val trustAllCerts = arrayOf<TrustManager>(object : javax.net.ssl.X509TrustManager {
        override fun getAcceptedIssuers(): Array<out X509Certificate> {
            return emptyArray()
        }

        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }
    })

    // Install the all-trusting trust manager
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
    // Create an ssl socket factory with our all-trusting manager
    val sslSocketFactory = sslContext.socketFactory

    builder.sslSocketFactory(sslSocketFactory)
    builder.hostnameVerifier { hostname, session -> true }
}

fun main(args: Array<String>) {
    val builder = OkHttpClient.Builder()
    // allowProxy(builder)
    builder.connectTimeout(60, TimeUnit.SECONDS)
    builder.readTimeout(60, TimeUnit.SECONDS)
    builder.writeTimeout(60, TimeUnit.SECONDS)
    val http = builder.build()

    val properties = Properties()

    val input = FileInputStream("config.properties")
    input.use {
        properties.load(it)
    }
    input.close()

    val settings = Settings(properties)

    val username = settings.username
    val password = settings.password

    val token = settings.token

    Log.normal("Logging in to game server...")
    val auth: RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo

    auth = if (token.isBlank()) {
        if (username.contains('@')) {
            GoogleLogin(http).login(username, password)
        } else {
            PtcLogin(http).login(username, password)
        }
    } else {
        if (token.contains("pokemon.com")) {
            PtcLogin(http).login(token)
        } else {
            GoogleLogin(http).login(token)
        }
    }

    Log.normal("Logged in as $username with token ${auth.token.contents}")

    if (token.isBlank()) {
        Log.normal("Setting this token in your config")
        settings.setToken(auth.token.contents)
        val out = FileOutputStream("config.properties")
        settings.properties.store(out,null)
        out.close()

    }
    val api = PokemonGo(auth, http)

    print("Getting profile data from pogo server")
    while (api.playerProfile == null) {
        print(".")
        Thread.sleep(1000)
    }
    println(".")

    Bot(api, settings).run()
}
