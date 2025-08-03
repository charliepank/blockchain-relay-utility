package com.utility.chainservice.models

import io.swagger.v3.oas.annotations.media.Schema

data class TransactionResult(
    @Schema(
        description = "Whether the transaction was successfully submitted to the blockchain",
        example = "true"
    )
    val success: Boolean,
    
    @Schema(
        description = "Transaction hash if the transaction was successful, null if failed",
        example = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
        pattern = "^0x[a-fA-F0-9]{64}$"
    )
    val transactionHash: String?,
    
    @Schema(
        description = "Error message if the transaction failed, null if successful",
        example = "Insufficient gas for transaction"
    )
    val error: String? = null
)