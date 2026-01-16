package com.example.runpodmanager.data.ssh

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringReader
import java.net.InetAddress
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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

    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

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
                allocateDefaultPTY()
            }
            Log.d(TAG, "PTY asignado!")

            Log.d(TAG, "Iniciando shell...")
            shell = session?.startShell()
            Log.d(TAG, "Shell iniciado!")

            // Configurar streams
            shell?.let { sh ->
                writer = PrintWriter(sh.outputStream, true)
                reader = BufferedReader(InputStreamReader(sh.inputStream))
            }

            _connectionState.value = SshConnectionState.Connected

            // Iniciar lectura de salida
            startOutputReader()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error de conexion: ${e.message}", e)
            _connectionState.value = SshConnectionState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    private suspend fun startOutputReader(): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando lector de salida...")
            val buffer = CharArray(1024)

            while (coroutineContext.isActive && shell?.isOpen == true) {
                try {
                    if (reader?.ready() == true) {
                        val read = reader?.read(buffer) ?: 0
                        if (read > 0) {
                            val text = String(buffer, 0, read)
                            _terminalOutput.value += text
                        }
                    } else {
                        delay(50)
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

    suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Enviando comando: $command")
            writer?.println(command)
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando comando: ${e.message}", e)
            _connectionState.value = SshConnectionState.Error(e.message ?: "Send error")
        }
    }

    suspend fun sendKey(key: Char) = withContext(Dispatchers.IO) {
        try {
            writer?.print(key)
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando tecla: ${e.message}", e)
            _connectionState.value = SshConnectionState.Error(e.message ?: "Send error")
        }
    }

    fun disconnect() {
        Log.d(TAG, "Desconectando...")
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
            writer = null
            reader = null
            _connectionState.value = SshConnectionState.Disconnected
            Log.d(TAG, "Desconectado")
        }
    }

    fun clearOutput() {
        _terminalOutput.value = ""
    }

    fun isConnected(): Boolean = sshClient?.isConnected == true && shell?.isOpen == true
}

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    object Connected : SshConnectionState()
    data class Error(val message: String) : SshConnectionState()
}
