package it.palsoftware.pastiera.adb.shell

import android.content.Context
import android.os.Process as AndroidProcess
import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Commands through the shell helper ([ShellServer]) once it's running: the same ADB shell
 * Shizuku gives, without Shizuku.
 */
object BuiltInShell {

    private val ports = ShellProtocol.ports(AndroidProcess.myUid())
    @Volatile private var token: String? = null
    @Volatile private var port: Int? = null
    @Volatile private var appContext: Context? = null

    /** Loads (or makes) the token the helper is started with. Called as the app starts. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (token != null) return
        val file = File(File(context.noBackupFilesDir, "shell").apply { mkdirs() }, "token")
        token = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.length >= 32 }
            ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
                .also { file.writeText(it) }
    }

    internal fun token(context: Context): String {
        init(context)
        return token!!
    }

    @Volatile private var lastCheck = 0L
    @Volatile private var lastRunning = false
    private val startListeners = CopyOnWriteArrayList<() -> Unit>()

    /** The helper is answering. Checked at most once a second. */
    fun running(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 1_000) return lastRunning
        val running = runCatching {
            ShellThreads.offMain { open(listOf(ShellProtocol.PING)).waitFor(1_000, TimeUnit.MILLISECONDS) }
        }.getOrDefault(false)
        lastCheck = now
        if (running && !lastRunning) startListeners.forEach { runCatching { it() } }
        // Found gone after running: started again (now, or once Wi-Fi connects)
        if (!running && lastRunning) appContext?.let { ShellSetup.ensureStarted(it) }
        lastRunning = running
        return running
    }

    /** Called (off the main thread) when the helper is found running after it wasn't. */
    fun addStartListener(listener: () -> Unit) { startListeners += listener }
    fun removeStartListener(listener: () -> Unit) { startListeners -= listener }

    internal fun markStarted() {
        lastCheck = 0L
        running()
    }

    /** Starts [argv] as the shell user; null when the helper isn't running. */
    fun newProcess(argv: Array<String>): Process? =
        if (running()) runCatching { ShellThreads.offMain { open(argv.toList()) } }.getOrNull() else null

    private fun open(argv: List<String>): ShellProcess {
        val token = token ?: throw IOException("not set up")
        val socket = port?.let { ShellProtocol.handshake(it, token) }
            ?: ports.firstNotNullOfOrNull { candidate ->
                ShellProtocol.handshake(candidate, token)?.also { port = candidate }
            }
            ?: throw IOException("the shell helper isn't running")
        return ShellProcess(socket, argv)
    }
}

/** A command run by the helper, read and written like any other [Process]. */
internal class ShellProcess(private val socket: Socket, argv: List<String>) : Process() {
    private val out = ChannelStream()
    private val err = ChannelStream()
    private val writer = DataOutputStream(socket.getOutputStream())
    private val lock = Object()
    @Volatile private var exit: Int? = null

    init {
        synchronized(writer) {
            writer.writeInt(argv.size)
            argv.forEach { writer.writeUTF(it) }
            writer.flush()
        }
        Thread { readLoop(DataInputStream(socket.getInputStream())) }.apply { isDaemon = true; start() }
    }

    private fun readLoop(input: DataInputStream) {
        var code = -1
        try {
            loop@ while (true) {
                when (input.readByte().toInt()) {
                    ShellProtocol.STDOUT -> out.append(input.readChunk())
                    ShellProtocol.STDERR -> err.append(input.readChunk())
                    ShellProtocol.EXIT -> { code = input.readInt(); break@loop }
                }
            }
        } catch (_: IOException) {
        }
        finish(code)
    }

    private fun DataInputStream.readChunk(): ByteArray = ByteArray(readInt()).also { readFully(it) }

    private fun finish(code: Int) {
        synchronized(lock) {
            if (exit != null) return
            exit = code
            lock.notifyAll()
        }
        out.end()
        err.end()
        runCatching { socket.close() }
    }

    override fun getOutputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) = ShellThreads.offMain {
            synchronized(writer) {
                writer.writeByte(ShellProtocol.STDIN)
                writer.writeInt(len)
                writer.write(b, off, len)
            }
        }
        override fun flush() = ShellThreads.offMain { synchronized(writer) { writer.flush() } }
        override fun close() {
            runCatching {
                ShellThreads.offMain {
                    synchronized(writer) { writer.writeByte(ShellProtocol.STDIN_END); writer.flush() }
                }
            }
        }
    }

    override fun getInputStream(): InputStream = out
    override fun getErrorStream(): InputStream = err

    override fun waitFor(): Int {
        synchronized(lock) { while (exit == null) lock.wait() }
        return exit!!
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        val end = System.currentTimeMillis() + unit.toMillis(timeout)
        synchronized(lock) {
            while (exit == null) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
        }
        return true
    }

    override fun exitValue(): Int = exit ?: throw IllegalThreadStateException("still running")

    override fun destroy() {
        runCatching { socket.shutdownOutput() }
        runCatching { socket.close() }
        finish(exit ?: -1)
    }

    override fun isAlive(): Boolean = exit == null
}

/** Output arriving from the helper, read like a pipe. */
private class ChannelStream : InputStream() {
    private val chunks = ArrayDeque<ByteArray>()
    private var position = 0
    private var ended = false
    private val lock = Object()

    fun append(chunk: ByteArray) = synchronized(lock) { chunks.addLast(chunk); lock.notifyAll() }
    fun end() = synchronized(lock) { ended = true; lock.notifyAll() }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        synchronized(lock) {
            while (chunks.isEmpty()) {
                if (ended) return -1
                lock.wait()
            }
            val chunk = chunks.first()
            val count = minOf(len, chunk.size - position)
            System.arraycopy(chunk, position, b, off, count)
            position += count
            if (position == chunk.size) { chunks.removeFirst(); position = 0 }
            return count
        }
    }

    override fun available(): Int = synchronized(lock) {
        chunks.sumOf { it.size } - position.coerceAtMost(chunks.firstOrNull()?.size ?: 0)
    }
}

/** Sockets aren't allowed on the main thread: there they're opened on another, waited for. */
internal object ShellThreads {
    private val pool = java.util.concurrent.Executors.newCachedThreadPool()

    private fun onMain(): Boolean = runCatching {
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
    }.getOrDefault(false)

    fun <T> offMain(block: () -> T): T =
        if (onMain()) pool.submit<T> { block() }.get(3, TimeUnit.SECONDS) else block()
}
