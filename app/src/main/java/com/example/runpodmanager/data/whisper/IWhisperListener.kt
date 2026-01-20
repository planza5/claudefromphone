package com.example.runpodmanager.data.whisper

interface IWhisperListener {
    fun onUpdateReceived(message: String)
    fun onResultReceived(result: String)
}
