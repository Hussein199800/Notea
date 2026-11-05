package com.example.data.transfer

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * Phone-to-phone transfer on the local network (same Wi-Fi, or one phone's hotspot), no internet
 * and no account needed. The old phone serves a password-encrypted .notea backup over a tiny TCP
 * protocol; the one-time code is the backup password, shown only on its screen (QR or text).
 *
 * Protocol: client -> "NOTEA1 <code>\n"; server -> "OK <bytes>\n" + data, or "ERR <reason>\n".
 * (Raw TCP: no cleartext HTTP is involved.)
 */
object DeviceTransfer {
    const val SCHEME = "notea-transfer"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I
    private const val MAGIC = "NOTEA1"

    data class Offer(val host: String, val port: Int, val code: String) {
        fun toUri() = "$SCHEME://$host:$port/$code"
        /** Human-friendly version for typing: "192.168.1.5:40123  ABCD-EFGH-JKLM". */
        val address get() = "$host:$port"
        val prettyCode get() = code.chunked(4).joinToString("-")
    }

    fun newCode(): String = SecureRandom().let { r -> buildString { repeat(12) { append(ALPHABET[r.nextInt(ALPHABET.length)]) } } }

    fun parse(text: String): Offer? {
        val t = text.trim()
        Regex("^$SCHEME://([0-9.]+):(\\d+)/([A-Z2-9]{12})$").find(t)?.let { m ->
            return Offer(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
        }
        return null
    }

    fun parseManual(address: String, code: String): Offer? {
        val a = address.trim().replace(" ", "")
        val host = a.substringBefore(':'); val port = a.substringAfter(':', "").toIntOrNull() ?: return null
        val c = code.uppercase().filter { it in ALPHABET }
        if (!Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(host) || c.length != 12) return null
        return Offer(host, port, c)
    }

    /** IPv4 of the Wi-Fi / hotspot interface (prefers wlan/ap/swlan). */
    fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { n -> if (listOf("wlan", "ap", "swlan", "softap").any { n.name.startsWith(it) }) 0 else 1 }
            .flatMap { n -> n.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { it.hostAddress } }
            .firstOrNull()
    }.getOrNull()

    fun qrBitmap(content: String, size: Int = 720, dark: Int = 0xFF07382A.toInt(), light: Int = 0xFFFAF6EC.toInt()): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) dark else light }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * Sender: serves [file] to the first client presenting [code], then stops.
     * Wrong codes are rejected; after 5 wrong attempts the server closes.
     */
    class Server(private val file: File, private val code: String) {
        private val socket = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) }
        val port: Int get() = socket.localPort
        @Volatile var closed = false; private set

        suspend fun serve(onProgress: (Long, Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
            var wrong = 0
            socket.soTimeout = 15 * 60_000
            try {
                while (!closed) {
                    val client = try { socket.accept() } catch (e: Exception) { return@withContext false }
                    client.use { c ->
                        c.soTimeout = 30_000
                        val line = readLine(c.getInputStream())
                        val out = c.getOutputStream()
                        if (line == "$MAGIC $code") {
                            val size = file.length()
                            out.write("OK $size\n".toByteArray()); out.flush()
                            var sent = 0L
                            file.inputStream().use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = input.read(buf); if (r < 0) break
                                    out.write(buf, 0, r); sent += r; onProgress(sent, size)
                                }
                            }
                            out.flush()
                            return@withContext true
                        } else {
                            out.write("ERR code\n".toByteArray()); out.flush()
                            if (++wrong >= 5) return@withContext false
                        }
                    }
                }
                false
            } finally {
                close()
            }
        }

        fun close() { closed = true; runCatching { socket.close() } }
    }

    /** Receiver: downloads the backup into [target]. Returns false on wrong code / network errors. */
    suspend fun receive(offer: Offer, target: File, onProgress: (Long, Long) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(offer.host, offer.port), 10_000)
                s.soTimeout = 30_000
                s.getOutputStream().apply { write("$MAGIC ${offer.code}\n".toByteArray()); flush() }
                val input = BufferedInputStream(s.getInputStream())
                val header = readLine(input)
                if (!header.startsWith("OK ")) throw IllegalStateException("rejected")
                val size = header.removePrefix("OK ").trim().toLong()
                var got = 0L
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (got < size) {
                        val r = input.read(buf, 0, minOf(buf.size.toLong(), size - got).toInt()); if (r < 0) break
                        out.write(buf, 0, r); got += r; onProgress(got, size)
                    }
                }
                if (got != size) throw IllegalStateException("incomplete")
            }
        }
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (sb.length < 256) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }
}
