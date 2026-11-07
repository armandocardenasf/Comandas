package com.enverolab.comandas.io

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import java.io.File
import java.io.FileOutputStream

object PdfPrinter {
    fun print(context: Context, file: File) {
        context
            .getSystemService(PrintManager::class.java)
            .print(
                file.name,
                object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback,
                        extras: Bundle?,
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onLayoutCancelled()
                            return
                        }
                        callback.onLayoutFinished(
                            PrintDocumentInfo.Builder(file.name)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                                .build(),
                            true,
                        )
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback,
                    ) {
                        try {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                return
                            }
                            file.inputStream().use { source ->
                                FileOutputStream(destination.fileDescriptor).use {
                                    source.copyTo(it)
                                }
                            }
                            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback.onWriteFailed(e.message)
                        }
                    }
                },
                null,
            )
    }
}
