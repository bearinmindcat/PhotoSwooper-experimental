package com.example.photoswooper.experimental.ConversionPipelineDocView

enum class DocRenderMethod {
    PLAIN_TEXT,
    WEBVIEW;

    companion object {
        fun getActive(webViewEnabled: Boolean): DocRenderMethod =
            if (webViewEnabled) WEBVIEW else PLAIN_TEXT
    }
}
