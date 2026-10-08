package it.palsoftware.pastiera.adb.shell

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * The shell helper: started once per boot through wireless debugging with
 * `app_process`, it runs as the ADB shell user and keeps running after wireless debugging is
 * off again. It listens on a local port and only answers whoever gives the token Flux Keyboard
 * started it with (an app's private file, passed in the environment, which other apps can't read).
 *
 * Each connection runs one command. The client sends the arguments, then framed input; the
 * helper sends back framed output and the exit code ([ShellProtocol]). Closing the connection
 * stops the command.
 */
object ShellServer {

    @JvmStatic
    fun main(args: Array<String>) {
        val uid = args.getOrNull(0)?.toIntOrNull() ?: exitProcess(1)
        val token = System.getenv(ShellProtocol.TOKEN_ENV)?.takeIf { it.isNotEmpty() } ?: exitProcess(1)
        exitProcess(serve(uid, token))
    }

    /** Answers until it can't; the exit code. */
    internal fun serve(uid: Int, token: String): Int {
        // Another helper is already answering
        if (ShellProtocol.ports(uid).any { ShellProtocol.handshake(it, token)?.close() != null }) return 0
        val server = ShellProtocol.ports(uid).firstNotNullOfOrNull { port ->
            runCatching { ServerSocket(port, 50, InetAddress.getByName("127.0.0.1")) }.getOrNull()
        } ?: return 1
        val expected = token.toByteArray()
        while (true) {
            val socket = runCatching { server.accept() }.getOrNull() ?: continue
            Thread {
                val authorised = runCatching {
                    socket.soTimeout = 5_000
                    val given = DataInputStream(socket.getInputStream()).readUTF().toByteArray()
                    MessageDigest.isEqual(given, expected)
                }.getOrDefault(false)
                if (!authorised) {
                    runCatching { socket.close() }
                    return@Thread
                }
                runCatching {
                    socket.soTimeout = 0
                    socket.getOutputStream().apply { write(ShellProtocol.ACCEPTED); flush() }
                }.onFailure { runCatching { socket.close() }; return@Thread }
                serve(socket)
            }.apply { isDaemon = true; start() }
        }
    }

    private fun serve(socket: Socket) {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val process = runCatching {
            val argv = List(input.readInt()) { input.readUTF() }
            if (argv == listOf(ShellProtocol.PING)) {
                output.writeByte(ShellProtocol.EXIT)
                output.writeInt(0)
                output.flush()
                socket.close()
                return
            }
            ProcessBuilder(argv).start()
        }.getOrElse {
            runCatching {
                output.writeByte(ShellProtocol.EXIT)
                output.writeInt(127)
                output.flush()
            }
            runCatching { socket.close() }
            return
        }
        val writeLock = Any()
        fun pump(stream: InputStream, type: Int) = Thread {
            val buffer = ByteArray(8192)
            runCatching {
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    synchronized(writeLock) {
                        output.writeByte(type)
                        output.writeInt(read)
                        output.write(buffer, 0, read)
                        output.flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val out = pump(process.inputStream, ShellProtocol.STDOUT)
        val err = pump(process.errorStream, ShellProtocol.STDERR)
        // The client's input frames go to the command; the connection closing stops it
        Thread {
            runCatching {
                while (true) {
                    when (input.readByte().toInt()) {
                        ShellProtocol.STDIN -> {
                            val data = ByteArray(input.readInt())
                            input.readFully(data)
                            process.outputStream.write(data)
                            process.outputStream.flush()
                        }
                        ShellProtocol.STDIN_END -> runCatching { process.outputStream.close() }
                    }
                }
            }
            process.destroy()
        }.apply { isDaemon = true; start() }
        val code = process.waitFor()
        out.join(2_000)
        err.join(2_000)
        runCatching {
            synchronized(writeLock) {
                output.writeByte(ShellProtocol.EXIT)
                output.writeInt(code)
                output.flush()
            }
        }
        runCatching { socket.close() }
    }
}

internal object ShellProtocol {
    const val STDOUT = 1
    const val STDERR = 2
    const val EXIT = 3
    const val STDIN = 4
    const val STDIN_END = 5
    const val ACCEPTED = 0x46
    const val PING = "\u0000ping"
    const val TOKEN_ENV = "FLUX_SHELL_TOKEN"

    /** The local ports tried in turn: from the app's uid, so each user profile's app differs. */
    fun ports(uid: Int): List<Int> = (0 until 3).map { 41_000 + uid % 20_000 + it * 2_000 }

    /** Connects and gives the token: the open socket once accepted, else null. */
    fun handshake(port: Int, token: String): Socket? {
        val socket = Socket()
        return runCatching {
            socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            socket.soTimeout = 2_000
            DataOutputStream(socket.getOutputStream()).apply { writeUTF(token); flush() }
            check(socket.getInputStream().read() == ACCEPTED)
            socket.soTimeout = 0
            socket
        }.getOrElse {
            runCatching { socket.close() }
            null
        }
    }
}
