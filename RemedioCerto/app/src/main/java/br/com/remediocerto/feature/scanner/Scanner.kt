package br.com.remediocerto.feature.scanner

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

fun iniciarScanner(context: Context, onCodigo: (String) -> Unit, onErro: (String) -> Unit) {
    val opcoes = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
        )
        .enableAutoZoom()
        .build()

    GmsBarcodeScanning.getClient(context, opcoes)
        .startScan()
        .addOnSuccessListener { codigo -> codigo.rawValue?.let(onCodigo) }
        .addOnFailureListener { onErro("Não foi possível abrir o leitor: ${it.message}") }
    // Se o usuário fechar a câmera sem ler nada, simplesmente não acontece nada.
}