package com.arthenica.ffmpegkit

class FFmpegKit {
    companion object {
        @JvmStatic fun execute(command: String) = FFmpegSession()
        @JvmStatic fun executeAsync(command: String, callback: Any) = FFmpegSession()
    }
}

class FFmpegKitConfig {
    companion object {
        @JvmStatic fun enableLogCallback(callback: Any) {}
        @JvmStatic fun enableStatisticsCallback(callback: Any) {}
    }
}

class FFmpegSession {
    fun getReturnCode() = ReturnCode()
    fun getFailStackTrace() = ""
    fun getState(): Any? = null
}

class ReturnCode {
    fun isSuccess() = true
    fun isCancel() = false
    fun isError() = false
}
