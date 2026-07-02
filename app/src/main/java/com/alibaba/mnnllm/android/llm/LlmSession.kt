package com.alibaba.mnnllm.android.llm

object LlmSession {
    external fun initNative(
        configPath: String?,
        history: List<String>?,
        mergedConfigStr: String?,
        configJsonStr: String?
    ): Long

    external fun submitNative(
        instanceId: Long,
        input: String,
        keepHistory: Boolean,
        listener: GenerateProgressListener
    ): HashMap<String, Any>?

    external fun releaseNative(instanceId: Long)
}
