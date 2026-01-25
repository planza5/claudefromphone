package com.example.runpodmanager.data.ssh

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.StringReader
import java.net.InetAddress
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SshManager"

@Singleton
class SshManager @Inject constructor(
    private val sshKeyManager: SshKeyManager
) {
    init {
        // Registrar BouncyCastle como proveedor de seguridad con alta prioridad
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    // Configuración personalizada que excluye algoritmos problemáticos en Android
    private fun createSshConfig(): DefaultConfig {
        return DefaultConfig().apply {
            // Filtrar key exchange algorithms que usan X25519 (no soportado en Android)
            keyExchangeFactories = keyExchangeFactories.filter { factory ->
                !factory.name.contains("curve25519", ignoreCase = true)
            }
        }
    }

    // Scope independiente para el lector de output - no se cancela con el ViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readerJob: Job? = null

    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _rawOutput = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rawOutput: SharedFlow<ByteArray> = _rawOutput.asSharedFlow()

    suspend fun connect(
        host: String,
        port: Int,
        username: String = "root"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Conectando a $host:$port como $username")
            _connectionState.value = SshConnectionState.Connecting

            val ssh = SSHClient(createSshConfig()).apply {
                // Usar verificador promiscuo (en producción usar verificación real)
                addHostKeyVerifier(PromiscuousVerifier())

                // Keepalive para mantener conexión activa
                connection.keepAlive.keepAliveInterval = 30 // cada 30 segundos

                Log.d(TAG, "Conectando al servidor SSH...")
                try {
                    // Forzar IPv4 resolviendo la dirección explícitamente
                    val ipv4Address = InetAddress.getByName(host)
                    Log.d(TAG, "Resolviendo $host -> ${ipv4Address.hostAddress}")
                    connect(ipv4Address.hostAddress, port)
                    Log.d(TAG, "Conexion TCP establecida!")
                } catch (e: Exception) {
                    Log.e(TAG, "Error de conexion TCP: ${e.message}", e)
                    throw Exception("No se puede conectar a $host:$port - ${e.message}", e)
                }

                // Autenticacion con clave privada
                val privateKey = sshKeyManager.getPrivateKey()
                if (privateKey != null) {
                    Log.d(TAG, "Clave privada encontrada, longitud: ${privateKey.length}")
                    Log.d(TAG, "Primeros 50 chars: ${privateKey.take(50)}")
                    try {
                        // Cargar clave desde string usando PKCS8KeyFile
                        val keyProvider = PKCS8KeyFile().apply {
                            init(StringReader(privateKey), null)
                        }
                        Log.d(TAG, "Clave privada cargada, autenticando...")
                        authPublickey(username, keyProvider)
                        Log.d(TAG, "Autenticacion exitosa!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error de autenticacion: ${e.message}", e)
                        disconnect()
                        throw Exception("Error de autenticacion - ${e.message}", e)
                    }
                } else {
                    Log.e(TAG, "No se encontro clave privada")
                    disconnect()
                    throw Exception("No se encontro clave privada - genera una en Configuracion")
                }
            }

            sshClient = ssh

            // Iniciar sesion y shell
            Log.d(TAG, "Iniciando sesion...")
            session = ssh.startSession().apply {
                // Usar xterm-256color para mejor soporte de secuencias ANSI
                // Dimensiones iniciales: 80x24, se pueden ajustar después
                allocatePTY("xterm-256color", 80, 24, 0, 0, emptyMap())
            }
            Log.d(TAG, "PTY asignado con xterm-256color!")

            Log.d(TAG, "Iniciando shell...")
            shell = session?.startShell()
            Log.d(TAG, "Shell iniciado!")

            // Configurar streams
            shell?.let { sh ->
                inputStream = sh.inputStream
                outputStream = sh.outputStream
            }

            _connectionState.value = SshConnectionState.Connected

            // Iniciar lectura de salida en scope independiente
            startOutputReader()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error de conexion: ${e.message}", e)
            _connectionState.value = SshConnectionState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    private fun startOutputReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                Log.d(TAG, "Iniciando lector de salida en scope independiente...")
                val buffer = ByteArray(4096)
                val stream = inputStream ?: return@launch

                while (isActive && shell?.isOpen == true) {
                    try {
                        val available = stream.available()
                        if (available > 0) {
                            val read = stream.read(buffer, 0, minOf(available, buffer.size))
                            if (read > 0) {
                                _rawOutput.emit(buffer.copyOf(read))
                            }
                        } else {
                            delay(10)
                        }
                    } catch (e: Exception) {
                        if (shell?.isOpen == true) {
                            Log.e(TAG, "Error leyendo salida: ${e.message}")
                        }
                        break
                    }
                }
                Log.d(TAG, "Lector de salida terminado")
            } catch (e: Exception) {
                if (shell?.isOpen == true) {
                    Log.e(TAG, "Error en lector: ${e.message}", e)
                    _connectionState.value = SshConnectionState.Error(e.message ?: "Read error")
                }
            }
        }
    }

    suspend fun sendCommand(command: String) = sendRawBytes(command.toByteArray(Charsets.UTF_8))

    suspend fun sendKey(key: Char) = sendRawBytes(key.toString().toByteArray(Charsets.UTF_8))

    suspend fun sendRawBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando datos: ${e.message}", e)
            _connectionState.value = SshConnectionState.Error(e.message ?: "Send error")
        }
    }

    fun disconnect() {
        Log.d(TAG, "Desconectando...")
        readerJob?.cancel()
        readerJob = null
        try {
            shell?.close()
            session?.close()
            sshClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar: ${e.message}")
        } finally {
            shell = null
            session = null
            sshClient = null
            inputStream = null
            outputStream = null
            _connectionState.value = SshConnectionState.Disconnected
            Log.d(TAG, "Desconectado")
        }
    }

    fun isConnected(): Boolean = sshClient?.isConnected == true && shell?.isOpen == true

    /**
     * Ejecuta un comando y devuelve el resultado como String.
     * Usa una sesión separada para no interferir con el shell interactivo.
     */
    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = sshClient ?: return@withContext Result.failure(Exception("No conectado"))
            val execSession = client.startSession()
            val cmd = execSession.exec(command)
            val output = cmd.inputStream.bufferedReader().readText()
            cmd.join()
            execSession.close()
            Result.success(output.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando comando: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Redimensionar el PTY cuando el terminal cambia de tamaño.
     * Esto es importante para que las aplicaciones como vim, htop, etc.
     * se rendericen correctamente.
     */
    fun resizePTY(cols: Int, rows: Int) {
        if (shell?.isOpen != true) {
            Log.d(TAG, "Shell no está abierto, ignorando resize")
            return
        }
        try {
            shell?.changeWindowDimensions(cols, rows, 0, 0)
            Log.d(TAG, "PTY redimensionado a ${cols}x${rows}")
        } catch (e: Exception) {
            // Solo log, no desconectar por error de resize
            Log.w(TAG, "Error redimensionando PTY (no crítico): ${e.message}")
        }
    }
}

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    object Connected : SshConnectionState()
    data class Error(val message: String) : SshConnectionState()
}
