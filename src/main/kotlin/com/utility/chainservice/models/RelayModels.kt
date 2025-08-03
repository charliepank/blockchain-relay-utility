package com.utility.chainservice.models

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigInteger

data class GasTransferRequest(
    @Schema(
        description = "User's wallet address that will receive gas transfer if needed",
        example = "0x742b35Cc6834C0532Fee23f35E4cdb41c176fBc2",
        pattern = "^0x[a-fA-F0-9]{40}$"
    )
    val userWalletAddress: String,
    
    @Schema(
        description = """
            Hex-encoded signed transaction ready for blockchain submission.
            
            **Transaction Format Requirements:**
            
            1. **Hex-encoded signed transactions**: Must be valid hex strings that can be decoded by Web3j's TransactionDecoder.decode()
            
            2. **Supported transaction types**:
               - Legacy transactions: Must have gasPrice field
               - EIP-1559 transactions: Must have maxFeePerGas field
            
            3. **Required transaction fields**:
               - to: Target contract or wallet address
               - value: Transaction value in wei (can be zero)
               - data: Transaction payload (contract method calls, parameters, etc.)
               - gasLimit: Maximum gas units the transaction can consume
               - Gas pricing (one of):
                 - gasPrice (for legacy transactions)
                 - maxFeePerGas (for EIP-1559 transactions)
            
            **Example Legacy Transaction JSON:**
            ```
            {
              "to": "0x742b35Cc6834C532532532532532532532532532",
              "value": "1000000000000000000",
              "data": "0xa9059cbb...",
              "gasLimit": "21000",
              "gasPrice": "25000000000",
              "nonce": "42"
            }
            ```
            
            **Example EIP-1559 Transaction JSON:**
            ```
            {
              "to": "0x742b35Cc6834C532532532532532532532532532",
              "value": "1000000000000000000",
              "data": "0xa9059cbb...",
              "gasLimit": "21000",
              "maxFeePerGas": "30000000000",
              "maxPriorityFeePerGas": "2000000000",
              "nonce": "42"
            }
            ```
        """,
        example = "0xf86c2a8504a817c80082520894742b35cc6834c0532fee23f35e4cdb41c176fbc2880de0b6b3a764000080820a95a0c8b7b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3a01c8b7b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3",
        pattern = "^0x[a-fA-F0-9]+$"
    )
    val signedTransactionHex: String,
    
    @Schema(
        description = "Fallback gas operation name for cost estimation if transaction decoding fails",
        example = "transfer"
    )
    val fallbackGasOperation: String
)

data class OperationGasCost(
    @Schema(
        description = "Name of the blockchain operation",
        example = "transfer"
    )
    val operation: String,
    
    @Schema(
        description = "Maximum gas units this operation can consume",
        example = "21000"
    )
    val gasLimit: Long,
    
    @Schema(
        description = "Current gas price in wei",
        example = "25000000000"
    )
    val gasPriceWei: BigInteger,
    
    @Schema(
        description = "Total cost for this operation in wei (gasLimit * gasPriceWei)",
        example = "525000000000000"
    )
    val totalCostWei: BigInteger,
    
    @Schema(
        description = "Total cost for this operation formatted in AVAX",
        example = "0.000525"
    )
    val totalCostAvax: String
)