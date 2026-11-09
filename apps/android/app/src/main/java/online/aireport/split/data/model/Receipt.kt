package online.aireport.split.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptScanRequest(val imageBase64: String, val mediaType: String)

@Serializable
data class ReceiptLine(val name: String, val amountCents: Long)

@Serializable
data class ReceiptScan(
    val merchant: String? = null,
    val date: String? = null,
    val totalCents: Long? = null,
    val items: List<ReceiptLine> = emptyList(),
)
