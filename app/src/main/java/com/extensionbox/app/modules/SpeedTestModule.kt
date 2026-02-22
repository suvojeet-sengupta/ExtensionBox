package com.extensionbox.app.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.LinkedHashMap
import java.util.Locale

class SpeedTestModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var handler: Handler? = null
    private var testRunnable: Runnable? = null

    private var dlResult = "Waiting..."
    private var ulResult = "—"
    private var pingResult = "—"
    private var lastTestTime: Long = 0
    private var testsToday = 0
    private var testing = false

    companion object {
        private val TEST_URLS = arrayOf(
            "https://speed.cloudflare.com/__down?bytes=5000000",
            "https://proof.ovh.net/files/1Mb.dat",
            "https://ash-speed.hetzner.com/1MB.bin"
        )
        private const val UPLOAD_TEST_URL = "https://speed.cloudflare.com/__up"
    }

    override fun key(): String = "speedtest"
    override fun name(): String = "Speed Test"
    override fun emoji(): String = "🏎"
    override fun description(): String = "Periodic download/upload speed test"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 80

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "spd_interval", 30000) } ?: 30000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
        handler = Handler(Looper.getMainLooper())

        val autoTest = Prefs.getBool(ctx, "spd_auto_test", true)
        if (autoTest) {
            val freqMin = Prefs.getInt(ctx, "spd_test_freq", 60)
            testRunnable = object : Runnable {
                override fun run() {
                    if (!running) return
                    runTest()
                    handler?.postDelayed(this, freqMin * 60000L)
                }
            }
            handler?.postDelayed(testRunnable!!, 10000)
        }
    }

    override fun stop() {
        running = false
        handler?.removeCallbacksAndMessages(null)
    }

    override fun tick() {}

    fun runTestNow() {
        runTest()
    }

    private fun runTest() {
        val c = ctx ?: return
        if (testing) return

        val wifiOnly = Prefs.getBool(c, "spd_wifi_only", true)
        if (wifiOnly && !isOnWifi()) {
            dlResult = "Skipped (not WiFi)"
            lastTestTime = System.currentTimeMillis()
            return
        }

        val dailyLimit = Prefs.getInt(c, "spd_daily_limit", 10)
        if (dailyLimit in 1..9998 && testsToday >= dailyLimit) {
            dlResult = "Daily limit reached"
            lastTestTime = System.currentTimeMillis()
            return
        }

        testing = true
        dlResult = "Testing..."
        ulResult = "..."

        Thread {
            if (Prefs.getBool(c, "spd_show_ping", true)) {
                pingResult = doPing()
            }
            dlResult = doDownloadTest()
            ulResult = doUploadTest()
            testsToday++
            lastTestTime = System.currentTimeMillis()
            testing = false
        }.start()
    }

    private fun doPing(): String {
        return try {
            val start = System.currentTimeMillis()
            val sock = Socket()
            sock.connect(InetSocketAddress("1.1.1.1", 443), 5000)
            val elapsed = System.currentTimeMillis() - start
            sock.close()
            "${elapsed}ms"
        } catch (e: Exception) {
            try {
                val start = System.currentTimeMillis()
                val addr = InetAddress.getByName("1.1.1.1")
                if (addr.isReachable(5000)) {
                    val elapsed = System.currentTimeMillis() - start
                    "${elapsed}ms"
                } else "—"
            } catch (ignored: Exception) {
                "—"
            }
        }
    }

    private fun doDownloadTest(): String {
        for (testUrl in TEST_URLS) {
            try {
                val url = URL(testUrl)
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "ExtensionBox/1.0")
                c.setRequestProperty("Accept", "*/*")

                val start = System.currentTimeMillis()
                val is_ = c.inputStream
                val buf = ByteArray(32768)
                var total = 0L
                var r: Int

                while (is_.read(buf).also { r = it } != -1) {
                    total += r.toLong()
                    if (System.currentTimeMillis() - start > 15000) break
                }

                val ms = System.currentTimeMillis() - start
                is_.close()
                c.disconnect()

                if (ms > 0 && total > 0) {
                    val mbps = (total * 8.0) / (ms / 1000.0) / 1_000_000.0
                    return String.format(Locale.US, "%.1f Mbps", mbps)
                }
            } catch (e: Exception) {
                continue
            }
        }
        return "Failed"
    }

    private fun doUploadTest(): String {
        return try {
            val uploadData = ByteArray(1024 * 1024)
            for (i in uploadData.indices) {
                uploadData[i] = (i and 0xFF).toByte()
            }

            val url = URL(UPLOAD_TEST_URL)
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 30000
            c.doOutput = true
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setRequestProperty("Content-Length", uploadData.size.toString())
            c.setFixedLengthStreamingMode(uploadData.size)

            val start = System.currentTimeMillis()
            val os = c.outputStream
            var offset = 0
            val chunkSize = 32768
            while (offset < uploadData.size) {
                val len = Math.min(chunkSize, uploadData.size - offset)
                os.write(uploadData, offset, len)
                offset += len
                if (System.currentTimeMillis() - start > 15000) break
            }
            os.flush()
            os.close()

            c.responseCode
            val ms = System.currentTimeMillis() - start
            c.disconnect()

            if (ms > 0 && offset > 0) {
                val mbps = (offset * 8.0) / (ms / 1000.0) / 1_000_000.0
                String.format(Locale.US, "%.1f Mbps", mbps)
            } else "—"
        } catch (e: Exception) {
            "—"
        }
    }

    private fun isOnWifi(): Boolean {
        return try {
            val cm = ctx?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    override fun compact(): String = "🏎↓$dlResult"

    override fun detail(): String {
        val ago = if (lastTestTime > 0) (System.currentTimeMillis() - lastTestTime) / 60000 else -1
        val agoStr = if (ago < 0) "" else if (ago < 1) " (just now)" else " (${ago}m ago)"
        val ping = if (ctx?.let { Prefs.getBool(it, "spd_show_ping", true) } ?: true) " • Ping: $pingResult" else ""
        return "🏎 DL: $dlResult • UL: $ulResult$ping$agoStr\n   Tests today: $testsToday"
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["speedtest.download"] = dlResult
        d["speedtest.upload"] = ulResult
        d["speedtest.ping"] = pingResult
        d["speedtest.tests_today"] = testsToday.toString()
        return d
    }

    override fun checkAlerts(ctx: Context) {}
}
