package com.example.runpodmanager.data.ssh

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalSessionOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SshTerminalBridge"

/**
 * Bridge between SSH connection and Termux terminal emulator.
 *
 * This class creates a TerminalSession in "external mode" (no local PTY)
 * and redirects I/O between SSH and the terminal emulator.
 */
@Singleton
class SshTerminalBridge @Inject constructor(
    private val sshManager: SshManager
) : TerminalSessionOutput {

    private var session: TerminalSession? = null
    private var outputJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Create a new TerminalSession in external mode.
     * The session will use this bridge for output redirection.
     *
     * @param client The TerminalSessionClient to receive callbacks
     * @param transcriptRows Number of rows to keep in scroll history
     * @return The created TerminalSession
     */
    fun createSession(client: TerminalSessionClient, transcriptRows: Int = 2000): TerminalSession {
        Log.d(TAG, "Creating external terminal session with $transcriptRows transcript rows")
        val newSession = TerminalSession(client, transcriptRows, this)
        session = newSession
        return newSession
    }

    /**
     * Start collecting output from SSH and feeding it to the terminal.
     * Call this after the SSH connection is established.
     */
    fun startOutputCollection() {
        Log.d(TAG, "Starting SSH output collection")
        outputJob?.cancel()
        outputJob = scope.launch {
            sshManager.rawOutput.collect { bytes ->
                Log.v(TAG, "Received ${bytes.size} bytes from SSH")
                mainHandler.post {
                    session?.writeToTerminal(bytes, 0, bytes.size)
                }
            }
        }
    }

    /**
     * Stop output collection.
     */
    fun stopOutputCollection() {
        Log.d(TAG, "Stopping SSH output collection")
        outputJob?.cancel()
        outputJob = null
    }

    /**
     * TerminalSessionOutput implementation.
     * Called when the user types in the terminal - sends data to SSH.
     */
    override fun write(data: ByteArray, offset: Int, count: Int) {
        scope.launch {
            val bytes = if (offset == 0 && count == data.size) {
                data
            } else {
                data.copyOfRange(offset, offset + count)
            }
            Log.v(TAG, "Sending ${bytes.size} bytes to SSH")
            sshManager.sendRawBytes(bytes)
        }
    }

    /**
     * TerminalSessionOutput implementation.
     * Called when the terminal size changes - resize the SSH PTY.
     */
    override fun onResize(columns: Int, rows: Int) {
        Log.d(TAG, "Terminal resized to ${columns}x${rows}, updating SSH PTY")
        // Ejecutar en IO thread para evitar bloquear UI
        scope.launch {
            sshManager.resizePTY(columns, rows)
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up bridge resources")
        stopOutputCollection()
        session = null
    }

    /**
     * Full cleanup including scope cancellation.
     * Call this when the bridge is no longer needed.
     */
    fun destroy() {
        cleanup()
        scope.cancel()
    }

    /**
     * Get the current terminal session.
     */
    fun getSession(): TerminalSession? = session
}
