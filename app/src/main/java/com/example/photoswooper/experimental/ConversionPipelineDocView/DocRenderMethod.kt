package com.example.photoswooper.experimental.ConversionPipelineDocView

enum class DocRenderMethod {
    PLAIN_TEXT,
    WEBVIEW,
    LIBREOFFICE;

    companion object {
        fun getActive(webViewEnabled: Boolean, libreOfficeEnabled: Boolean): DocRenderMethod =
            when {
                libreOfficeEnabled -> LIBREOFFICE
                webViewEnabled -> WEBVIEW
                else -> PLAIN_TEXT
            }
    }
}
