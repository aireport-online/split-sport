package online.aireport.split.data

import java.util.Base64
import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.model.ReceiptScan
import online.aireport.split.data.model.ReceiptScanRequest

/** Reading the lines of a receipt from a photo, when the server supports it. */
class ReceiptRepository(private val gateway: ApiGateway) {
    suspend fun isScanEnabled(): Boolean = gateway.call { config() }.receiptScanEnabled

    suspend fun scan(jpeg: ByteArray): ReceiptScan =
        gateway.call {
            scanReceipt(ReceiptScanRequest(Base64.getEncoder().encodeToString(jpeg), mediaType = "image/jpeg"))
        }
}
