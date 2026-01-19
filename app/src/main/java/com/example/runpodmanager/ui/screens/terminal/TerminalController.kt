package com.example.runpodmanager.ui.screens.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlin.math.roundToInt

/**
 * Controller for TerminalView that implements both session and view client interfaces.
 * Can be created without a session and have the session set later.
 */
class TerminalController(
    private val context: Context
) : TerminalSessionClient, TerminalViewClient {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var terminalView: TerminalView? = null
    private var session: TerminalSession? = null
    private var baseTextSize = 56

    /**
     * Set the terminal session.
     * Can be called before or after attaching a view.
     */
    fun setSession(session: TerminalSession) {
        this.session = session
        terminalView?.attachSession(session)
    }

    /**
     * Attach a TerminalView to this controller.
     * If a session is already set, it will be attached to the view.
     */
    fun attachView(view: TerminalView) {
        if (terminalView === view) return
        terminalView = view
        view.setTerminalViewClient(this)
        session?.let { view.attachSession(it) }
        view.setTextSize(baseTextSize)
    }

    /**
     * Detach the current view.
     */
    fun detachView() {
        terminalView = null
    }

    /**
     * Set the text size for the terminal.
     */
    fun setTextSize(size: Int) {
        baseTextSize = size
        terminalView?.setTextSize(size)
    }

    /**
     * Get the attached TerminalView.
     */
    fun getView(): TerminalView? = terminalView

    /**
     * Show the soft keyboard for the terminal view.
     */
    fun showKeyboard() {
        val view = terminalView ?: return
        view.requestFocus()
        view.postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            // Try multiple methods to show keyboard
            if (!imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
            }
        }, 200)
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView?.postInvalidateOnAnimation()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        terminalView?.postInvalidateOnAnimation()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = clipboard.primaryClip
        val item = clip?.getItemAt(0)?.coerceToText(context)?.toString()
        val targetSession = session ?: this.session
        if (!item.isNullOrEmpty() && targetSession != null) {
            targetSession.write(item)
        }
    }

    override fun onBell(session: TerminalSession) {
        terminalView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onColorsChanged(session: TerminalSession) {
        terminalView?.postInvalidateOnAnimation()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        terminalView?.setTerminalCursorBlinkerState(state, true)
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int? = null

    override fun onScale(scale: Float): Float {
        val clamped = scale.coerceIn(0.5f, 4.0f)
        val textSize = (baseTextSize * clamped).roundToInt().coerceIn(12, 72)
        terminalView?.setTextSize(textSize)
        return clamped
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val view = terminalView ?: return
        view.requestFocus()
        // Show soft keyboard
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        terminalView?.postInvalidateOnAnimation()
    }

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, e.message, e)
    }
}
