# Blockchain Transaction Relay Utility

A generic Kotlin/Spring Boot utility for blockchain transaction relaying with gas management and pluggable business logic.

## What This Library Does

### Core Purpose
This library provides **"gas station as a service"** functionality for any EVM-compatible blockchain (Ethereum, Avalanche, Polygon, BSC, Arbitrum, etc.). It allows users to execute blockchain transactions without holding native tokens by sponsoring their gas costs through a smart contract fee collection system.

### Key Functionality

#### 🚗 **Transaction Gas Management & Sponsorship**
- **Decodes user-signed transactions** to extract gas requirements and validate authenticity
- **Checks user's native token balance** (ETH, AVAX, MATIC, etc.) before sponsoring
- **Smart funding logic**: Only transfers the difference if user has insufficient balance
- **Automatic gas sponsorship** via Gas Payer Contract with integrated service fee collection
- **Preserves original transaction signatures** - forwards user transactions unchanged to blockchain

#### 🔐 **Multi-Tenant Security System**
- **Hot-reloadable API key authentication** via JSON configuration file
- **Per-API-key wallet configuration** - each client can use their own private key/funding source
- **IP whitelisting** with support for exact IPs, wildcards, CIDR notation, and Docker service names
- **Economic attack prevention** through configurable gas cost/limit validation
- **Request rate limiting** and comprehensive security logging

#### 🔌 **Plugin Architecture for Business Logic**
- **Auto-discovery system** for business-specific functionality
- **Generic utility core** handles all blockchain operations, gas management, and security
- **Business plugins** implement custom logic without touching core infrastructure
- **Automatic REST API generation** with OpenAPI documentation

#### 🌐 **Universal Blockchain Compatibility**
- **Any EVM-compatible network** - just configure the RPC URL
- **Full Web3j integration** with transaction decoding/encoding
- **EIP-1559 and legacy transaction support**
- **Gas estimation and price calculation** with configurable multipliers
- **Receipt monitoring** with retry logic

### Transaction Flow Example

1. **Client Request**: User sends signed transaction via API key-protected endpoint
2. **Authentication**: System validates API key and loads client's wallet configuration  
3. **Balance Check**: Checks user's current native token balance on target blockchain
4. **Smart Funding**: If insufficient, transfers only the needed amount + service fee via Gas Payer Contract
5. **Balance Confirmation**: Waits for user's balance to update before proceeding
6. **Transaction Submission**: Forwards original signed transaction unchanged to blockchain
7. **Result**: Returns transaction hash and success/failure status

### Use Cases

- **DApps** that want to sponsor user transactions without requiring users to hold native tokens
- **Enterprise applications** needing controlled, secure blockchain access with custom business logic
- **Multi-client platforms** where different customers need separate funding sources and billing
- **Blockchain services** requiring gas abstraction with fee collection mechanisms

### Benefits

- **User Experience**: Users don't need native tokens to interact with blockchain
- **Cost Efficient**: Only sponsors the exact amount needed, not fixed amounts
- **Multi-Tenant**: Different API keys can use different funding wallets and security policies  
- **Extensible**: Plugin system allows custom business logic without core modifications
- **Secure**: Comprehensive authentication, IP whitelisting, and economic attack prevention
- **Universal**: Works with any EVM-compatible blockchain network

## Quick Reference for Integration

### Essential Dependencies
```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.charliepank:blockchain-relay-utility:v0.0.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")  // REQUIRED!
}
```

### Basic Controller Example
```kotlin
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.models.TransactionResult  // Note: .models package!
import kotlinx.coroutines.runBlocking  // For calling suspend functions

@RestController
class MyController(
    private val blockchainService: BlockchainRelayService  // Auto-injected
) {
    @PostMapping("/submit")
    fun submit(@RequestBody request: Request): ResponseEntity<Response> {
        // IMPORTANT: Use runBlocking for suspend functions
        // Use processTransactionWithGasTransfer for user-signed transactions
        val result = runBlocking {
            blockchainService.processTransactionWithGasTransfer(
                userWalletAddress = request.userAddress,
                signedTransactionHex = request.signedTransactionHex,
                operationName = "submit",
                expectedGasLimit = BigInteger.ZERO  // Falls back to max limits
            )
        }
        
        return ResponseEntity.ok(Response(
            success = result.success,
            transactionHash = result.transactionHash,
            message = result.error ?: "Success"
        ))
    }
}
```

### Test Mocking Example
```kotlin
import io.mockk.coEvery  // Use coEvery, not every!
import com.utility.chainservice.models.TransactionResult

@MockkBean
private lateinit var blockchainService: BlockchainRelayService

@Test
fun testSubmit() {
    // Mock suspend functions with coEvery
    coEvery { 
        blockchainService.processTransactionWithGasTransfer(any(), any(), any(), any()) 
    } returns TransactionResult(
        success = true,
        transactionHash = "0x123...",
        error = null
    )
}
```

## Features

- **Generic Transaction Relaying**: Core blockchain interaction and transaction forwarding
- **Gas Management with Fee Collection**: Automatic gas transfer via Gas Payer Contract with service fee collection
- **Three-Tier Gas Validation**: Validates transactions against configurable limits (total cost, gas limit, gas price)
- **API Key & IP Whitelist Security**: Hot-reloadable JSON configuration for authentication and IP restrictions
- **Flexible IP Patterns**: Supports exact IPs, wildcards, and CIDR notation
- **Plugin System**: Clean interface for implementing business-specific logic
- **Web3j Integration**: Full Ethereum-compatible blockchain support
- **Spring Boot Ready**: Auto-configuration and dependency injection

## Quick Start

### 1. Add Dependency

#### Via JitPack (Recommended)

Add JitPack repository to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.charliepank:blockchain-relay-utility:v0.0.11")
    // Or use latest:
    // implementation("com.github.charliepank:blockchain-relay-utility:main-SNAPSHOT")
    
    // Required: Coroutines for suspend functions
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Add your Web3j and Spring Boot dependencies
}
```

#### Via Maven Local (Development)

```kotlin
dependencies {
    implementation("com.utility:blockchain-relay-utility:1.0.0")
    // Add your Web3j and Spring Boot dependencies
}
```

### 2. Configure Application

The utility provides core blockchain configuration via `BlockchainProperties`. Add your business-specific properties separately to avoid conflicts.

```yaml
# application.yml
blockchain:
  rpcUrl: "https://api.avax-test.network/ext/bc/C/rpc"
  chainId: 43113  # Optional, auto-detected if not specified
  relayer:
    gasPayerContractAddress: "0x..." # Gas Payer Contract address
  gas:
    priceMultiplier: 1.2           # Gas price multiplier for relayer transactions
    minimumGasPriceWei: 6          # Minimum gas price in wei
    maxGasCostWei: 540000000       # Maximum total cost per transaction (security limit)
    maxGasLimit: 1000000           # Fallback maximum gas limit when no operation-specific limit is provided
    maxGasPriceMultiplier: 3       # Maximum gas price (3x current network price)

auth:
  userServiceUrl: "https://your-user-service.com"
  enabled: true

# Add your business-specific properties here
# DO NOT use 'blockchain' prefix for custom properties
your-service:
  contract-address: "0x..."
  custom-setting: "value"
```

### 3. Create Your Plugin

```kotlin
import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.plugin.BlockchainServicePlugin
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.stereotype.Component

@Component
class MyServicePlugin : BlockchainServicePlugin {
    override fun getPluginName(): String = "my-service"
    override fun getApiPrefix(): String = "/api/my-service"
    
    override fun initialize(relayService: BlockchainRelayService, authProvider: AuthenticationProvider) {
        // Initialize your business logic
    }
    
    override fun getGasOperations(): List<Pair<String, String>> = listOf(
        "myOperation" to "myOperation"
    )
    
    override fun getOpenApiTags(): List<Tag> = listOf(
        Tag().apply {
            name = "My Service"
            description = "My business-specific blockchain operations"
        }
    )
}
```

### 4. Create Your Controller

```kotlin
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.models.TransactionResult
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/my-service")
class MyServiceController(
    private val blockchainService: BlockchainRelayService  // Injected automatically
) {
    
    @PostMapping("/submit-transaction")
    fun submitTransaction(@RequestBody request: TransactionRequest): ResponseEntity<TransactionResponse> {
        // IMPORTANT: BlockchainRelayService methods are suspend functions
        // Use runBlocking to call them from non-suspend contexts
        // Use processTransactionWithGasTransfer for user-signed transactions
        val result = runBlocking {
            blockchainService.processTransactionWithGasTransfer(
                userWalletAddress = request.userAddress,
                signedTransactionHex = request.signedTransactionHex,
                operationName = "submitTransaction",
                expectedGasLimit = BigInteger.valueOf(150000)  // From Foundry testing
            )
        }
        
        return ResponseEntity.ok(TransactionResponse(
            success = result.success,
            transactionHash = result.transactionHash,
            message = result.error ?: "Transaction processed successfully"
        ))
    }
}
```

**Important Notes:**
- `BlockchainRelayService` is auto-configured and can be injected directly
- All transaction methods (`relayTransaction`, `processTransactionWithGasTransfer`) are **suspend functions**
- Use `runBlocking { }` to call suspend functions from regular Spring controllers
- Import `com.utility.chainservice.models.TransactionResult` (not `com.utility.chainservice.TransactionResult`)

### 5. Enable Component Scan

```kotlin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.yourcompany.yourapp", "com.utility.chainservice"])
class YourApplication
```

## Generic Endpoints

The utility provides these generic endpoints:

- `GET /api/relay/health` - Service health check with plugin status
- `GET /api/relay/gas-costs` - Current gas costs for all plugin operations

## Gas Validation and Management

### Operation-Specific Gas Limits

The utility supports validating transactions against operation-specific gas limits defined by plugins. This prevents users from requesting excessive gas for operations.

```kotlin
// Plugin code with operation-specific gas limits
val raiseDisputeGasLimit = BigInteger.valueOf(130000)  // Define expected gas for operation

val result = runBlocking {
    blockchainService.processTransactionWithGasTransfer(
        userWalletAddress = request.userAddress,
        signedTransactionHex = request.signedTransactionHex,
        operationName = "raiseDispute",
        expectedGasLimit = raiseDisputeGasLimit  // Validates against 130k + 20% buffer
    )
}
```

### Gas Validation Logic

The utility performs three-tier gas validation:

1. **Operation-specific validation** (when `expectedGasLimit > 0`):
   - Validates user's gas limit against expectedGasLimit + 20% buffer
   - Example: 130k expected → allows up to 156k
   - Prevents requesting excessive gas for known operations

2. **Fallback validation** (when `expectedGasLimit = 0`):
   - Uses configured `maxGasLimit` (default 1M gas)
   - Applied for unknown or unspecified operations
   - Also checks total cost against `maxGasCostWei` limit

3. **Always enforced checks**:
   - **Total cost limit**: `gasLimit × gasPrice` must not exceed `maxGasCostWei`
   - **Gas price limit**: User's gas price must not exceed `currentNetworkGasPrice × maxGasPriceMultiplier`
   - **Gas limit check**: User's gas limit validated against operation-specific or maximum limits

### Recommended Configuration

For services using operation-specific limits, set restrictive fallback limits:

```yaml
blockchain:
  gas:
    maxGasLimit: 1  # Set very low so unspecified operations fail
    maxGasCostWei: 1  # Forces use of operation-specific limits
```

This ensures all operations must provide explicit gas limits.

## Architecture

### Core Components

- **BlockchainRelayService**: Core transaction relaying and gas management
- **RelayController**: Generic utility REST endpoints
- **PluginConfiguration**: Auto-discovery and initialization of plugins

### Plugin Interface

Implement `BlockchainServicePlugin` to add your business logic:

```kotlin
import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import io.swagger.v3.oas.models.tags.Tag

interface BlockchainServicePlugin {
    fun getPluginName(): String
    fun getApiPrefix(): String
    fun getOpenApiTags(): List<Tag>
    fun initialize(relayService: BlockchainRelayService, authProvider: AuthenticationProvider)
    fun getGasOperations(): List<Pair<String, String>>
}
```

## Configuration

### Required Environment Variables

- `RPC_URL` - Blockchain RPC endpoint
- `GAS_PAYER_CONTRACT_ADDRESS` - Address of the Gas Payer Contract for fee collection

### Security Configuration File

The service uses a hot-reloadable JSON configuration file for API key authentication and IP whitelisting. This file can be updated without restarting the server.

### Property Configuration

The utility uses `@ConfigurationProperties(prefix = "blockchain")` for core blockchain settings. **Important**: Only use the `blockchain` prefix for utility-provided properties. For your business-specific configuration, create separate `@ConfigurationProperties` classes with different prefixes.

```yaml
# ✅ Correct - utility properties
blockchain:
  rpcUrl: "${RPC_URL}"
  chainId: 43113  # Default: auto-detected from RPC
  relayer:
    gasPayerContractAddress: "${GAS_PAYER_CONTRACT_ADDRESS}"
  gas:
    priceMultiplier: 1.2  # Default: 1.2x network gas price
    minimumGasPriceWei: 6  # Default: 6 wei minimum

# Security configuration
security:
  enabled: true  # Set to false to disable API key security
  configPath: "./config/security-config.json"  # Path to security configuration file

# ✅ Correct - your business properties
my-service:
  contractAddress: "${CONTRACT_ADDRESS}"
  feeAmount: 1000000

# ❌ Wrong - will conflict with utility
blockchain:
  contractAddress: "${CONTRACT_ADDRESS}"  # This conflicts!
```

### Example Business Properties Class

```kotlin
import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigInteger

@ConfigurationProperties(prefix = "my-service")
data class MyServiceProperties(
    var contractAddress: String = "",
    var feeAmount: BigInteger = BigInteger.ZERO
)
```

## Security & Authentication

### API Key & IP Whitelist Security

The utility provides a comprehensive security system using API keys and IP whitelisting with hot-reloadable configuration.

#### Security Configuration File Format

Create a `security-config.json` file (default location: `./config/security-config.json`):

```json
{
  "apiKeys": [
    {
      "key": "your_api_key_here",
      "name": "Production Client",
      "allowedIps": ["192.168.1.100", "10.0.0.0/24"],
      "enabled": true,
      "description": "Production environment client",
      "walletConfig": {
        "privateKey": "0x1234567890123456789012345678901234567890123456789012345678901234",
        "address": "0x1234567890123456789012345678901234567890"
      }
    },
    {
      "key": "dev_api_key_12345",
      "name": "Development Client", 
      "allowedIps": ["127.0.0.1", "192.168.*.*"],
      "enabled": true,
      "description": "Development environment with local access"
    }
  ],
  "globalIpWhitelist": ["127.0.0.1", "::1"],
  "settings": {
    "requireApiKey": true,
    "enforceIpWhitelist": true,
    "logFailedAttempts": true,
    "rateLimitEnabled": false,
    "rateLimitRequestsPerMinute": 60
  }
}
```

#### Configuration Fields

**API Key Configuration:**
- `key`: The API key string that clients must provide
- `name`: Human-readable name for the client
- `allowedIps`: List of allowed IP addresses/patterns (empty array = no IP restrictions)
- `enabled`: Whether this API key is currently active
- `description`: Optional description for documentation
- `walletConfig` (optional): Client-specific wallet configuration for blockchain operations
  - `privateKey`: Private key for this client's funding wallet (0x-prefixed hex, 66 chars)
  - `address`: Wallet address for this client (for validation)

**IP Address Patterns:**
- **Exact IP**: `"192.168.1.100"`
- **Wildcard**: `"192.168.1.*"` (matches 192.168.1.0-255)
- **CIDR notation**: `"10.0.0.0/24"` (matches 10.0.0.0-255)

**Settings:**
- `requireApiKey`: Enable/disable API key validation
- `enforceIpWhitelist`: Enable/disable IP address restrictions
- `logFailedAttempts`: Log authentication failures
- `rateLimitEnabled`: Enable rate limiting (future feature)
- `rateLimitRequestsPerMinute`: Requests per minute limit

#### Per-API-Key Wallet System

The library supports **client-specific wallets** for multi-tenant blockchain operations:

- **With `walletConfig`**: Client uses their own private key/wallet to fund transactions
- **Without `walletConfig`**: Transaction will fail (no default/shared wallet)
- **Hot-reloadable**: Wallet configurations can be updated without server restart
- **Secure**: Each client controls their own funding source and transaction costs

This architecture allows different customers to:
- Use separate funding sources
- Have independent cost control
- Maintain isolated blockchain operations
- Scale independently without shared wallet limitations

#### Client Usage Examples

Clients can authenticate using any of these methods:

```bash
# Method 1: X-API-Key header (recommended)
curl -H "X-API-Key: your_api_key_here" \
     http://localhost:8080/api/your-endpoint

# Method 2: Authorization Bearer header
curl -H "Authorization: Bearer your_api_key_here" \
     http://localhost:8080/api/your-endpoint

# Method 3: Query parameter (fallback)
curl "http://localhost:8080/api/your-endpoint?api_key=your_api_key_here"
```

#### Hot Reloading

The security configuration is automatically reloaded when the file changes:

1. Edit the `security-config.json` file
2. Save the changes
3. The service detects the file modification and reloads the configuration
4. New security rules take effect immediately (no server restart required)

#### Health Check Bypass

The following endpoints bypass security checks:
- `/actuator/health`
- `/health`
- `/ping` 
- `/status`

#### Security Logging

When `logFailedAttempts` is enabled, the service logs:
- Invalid API keys
- IP address rejections
- Authentication errors
- Successful authentications (debug level)



## Gas Management

The utility automatically:
1. **Validates gas limits** - Prevents economic attacks by checking gas limits and costs against configurable maximums
2. **Calculates exact gas costs** from user-signed transactions  
3. **Transfers native tokens** to user wallets if insufficient balance
4. **Forwards original signed transactions** unchanged
5. **Handles both transaction types** - Legacy and EIP-1559 transactions

### Gas Validation Security

The utility protects against economic attacks by validating:
- **Maximum total cost** - Prevents excessive transaction costs
- **Maximum gas limit** - Prevents excessive gas usage
- **Maximum gas price** - Prevents paying unreasonable gas prices (based on current network price)

**Why not gas estimation?** 
Many smart contracts have access controls (e.g., "only buyer can call"), making gas estimation impossible since the funding wallet can't execute the user's transaction for estimation. Instead, the utility uses configurable limits to prevent abuse while trusting that modern wallets provide reasonable gas estimates.

## Core API Methods

### BlockchainRelayService

The `BlockchainRelayService` provides the following suspend functions for transaction processing:

#### ⚠️ IMPORTANT: Method Selection Guide

**Choose the RIGHT method for your use case:**

- **Use `processTransactionWithGasTransfer()`** - ✅ **RECOMMENDED** for user-signed transactions that need gas funding
- **Use `relayTransaction()`** - ⚠️ **ONLY** for relayer-owned transactions or when you want to reconstruct the transaction

#### processTransactionWithGasTransfer() - ✅ RECOMMENDED
```kotlin
suspend fun processTransactionWithGasTransfer(
    userWalletAddress: String, 
    signedTransactionHex: String,
    fallbackGasOperation: String
): TransactionResult
```

**What it does:**
- ✅ **Preserves the user's original signature** - forwards transaction unchanged
- ✅ **Validates gas limits** to prevent economic attacks  
- ✅ **Transfers exact AVAX needed** to user's wallet if balance is insufficient
- ✅ **Forwards original signed transaction** unchanged to blockchain
- ✅ **Works with user-signed transactions** from wallets like MetaMask

**Use this when:** User signs a transaction client-side and you need to fund their gas costs.

**Usage:**
```kotlin
val result = runBlocking {
    blockchainService.processTransactionWithGasTransfer(
        userWalletAddress = "0x...",
        signedTransactionHex = "0xf86c...", // User's original signed transaction
        fallbackGasOperation = "defaultOperation"
    )
}
```

#### relayTransaction() - ⚠️ DANGEROUS for User Transactions
```kotlin
suspend fun relayTransaction(signedTransactionHex: String): TransactionResult
```

**What it does:**
- ❌ **RECONSTRUCTS the transaction** with different credentials (nonce, gas, signature)
- ❌ **INVALIDATES the original user signature**
- ❌ **CHANGES the transaction sender** to the funding wallet
- ⚠️ **Only works if funding wallet is authorized** to execute the transaction

**Use this ONLY when:** 
- The funding wallet owns/controls the transaction
- You want to reconstruct a transaction template with different credentials
- **NEVER use for user-signed transactions from wallets**

**Common Mistake:**
```kotlin
// ❌ WRONG - This will fail for user-signed transactions!
// The user signed with their address, but relayer reconstructs with relayer address
val result = runBlocking {
    blockchainService.relayTransaction(userSignedTxHex) // Will likely fail!
}

// ✅ CORRECT - Use this for user-signed transactions
val result = runBlocking {
    blockchainService.processTransactionWithGasTransfer(
        userWalletAddress = userAddress,
        signedTransactionHex = userSignedTxHex,
        fallbackGasOperation = "defaultOperation"
    )
}
```

**Why this matters:** Smart contracts often have access controls like "only buyer can claim" or "only token owner can transfer". When `relayTransaction()` reconstructs the transaction with the relayer's address, these access controls will reject the transaction because the relayer is not the authorized user.

### Important: Suspend Functions and Coroutines

**All `BlockchainRelayService` methods are suspend functions**, which means:

1. **In Controllers**: Use `runBlocking` to call from regular Spring methods:
```kotlin
@PostMapping("/submit")
fun submit(@RequestBody request: Request): ResponseEntity<Response> {
    val result = runBlocking {
        blockchainService.relayTransaction(request.hex)
    }
    // ...
}
```

2. **In Tests**: Use `coEvery` for mocking:
```kotlin
import io.mockk.coEvery

coEvery { 
    blockchainService.relayTransaction(any()) 
} returns TransactionResult(
    success = true,
    transactionHash = "0x123...",
    error = null
)
```

3. **Required Dependency**: Add kotlinx-coroutines to your build.gradle.kts:
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
```

## API Response Models

### TransactionResult

The `TransactionResult` model is returned by transaction relay operations:

```kotlin
// Import path: com.utility.chainservice.models.TransactionResult
data class TransactionResult(
    val success: Boolean,           // Whether the transaction was successful
    val transactionHash: String?,   // Transaction hash if successful
    val error: String? = null       // Error message if failed
)
```

**Example Response:**
```json
{
  "success": true,
  "transactionHash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
  "error": null,
  "contractAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f8E65"
}
```

The `contractAddress` field contains the destination address from the transaction, making it easy to track which contract was interacted with.

## Transaction Format Requirements

The utility expects properly formatted signed transactions with the following requirements:

### 1. Hex-encoded Signed Transactions
- Must be valid hex strings that can be decoded by Web3j's `TransactionDecoder.decode()`
- Should be complete, signed transactions ready for blockchain submission

### 2. Supported Transaction Types
- **Legacy transactions**: Must include `gasPrice` field for gas pricing
- **EIP-1559 transactions**: Must include `maxFeePerGas` field for gas pricing

### 3. Required Transaction Fields
All transactions must include:
- `to` - Target contract or wallet address
- `value` - Transaction value in wei (can be zero for contract calls)
- `data` - Transaction payload (contract method calls, parameters, etc.)
- `gasLimit` - Maximum gas units the transaction can consume
- **Gas pricing** (one of):
  - `gasPrice` for legacy transactions
  - `maxFeePerGas` for EIP-1559 transactions

### Example Transaction Formats

**Legacy Transaction:**
```json
{
  "to": "0x742b35Cc6834C532532532532532532532532532",
  "value": "1000000000000000000",
  "data": "0xa9059cbb...",
  "gasLimit": "21000",
  "gasPrice": "25000000000",
  "nonce": "42"
}
```

**EIP-1559 Transaction:**
```json
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

## JitPack Publishing

This library is published via JitPack for easy dependency management:

[![](https://jitpack.io/v/charliep/blockchain-relay-utility.svg)](https://jitpack.io/#charliep/blockchain-relay-utility)

To use in your project:
1. Add JitPack repository to your build file
2. Add the dependency with the desired version/branch
3. JitPack will automatically build and serve the library

### Available Versions
- `main-SNAPSHOT` - Latest development version
- `v1.0.0` - Stable release (when tagged)
- Any commit hash or branch name

## Migration Guide

### Version Compatibility

#### New in Latest Version: TransactionResult.contractAddress

The latest version adds a `contractAddress` field to the `TransactionResult` model. This change is **backward compatible**:

- **No code changes required** - Existing code will continue to work
- The new field is nullable with a default value of `null`
- When using `processTransactionWithGasTransfer`, the field is automatically populated with the transaction's destination address

**Optional Usage:**
```kotlin
// The contractAddress field is now available
val result = blockchainRelayService.processTransactionWithGasTransfer(...)
if (result.success) {
    println("Transaction sent to: ${result.contractAddress}")
}
```

### Upgrading from Legacy Controllers to Plugin Architecture

If you have existing blockchain transaction controllers, follow these steps to migrate to the plugin architecture:

#### 1. Remove Legacy Controllers
Remove existing controllers that directly implement blockchain transaction endpoints to avoid mapping conflicts.

**Example - Remove conflicting controllers:**
```kotlin
// ❌ Remove this - conflicts with plugin endpoints
@RestController
@RequestMapping("/api/chain")  
class ChainController {
    @PostMapping("/claim-funds")  // This conflicts with plugin
    fun claimFunds(request: ClaimFundsRequest): ResponseEntity<ClaimFundsResponse>
}
```

#### 2. Update Dependencies
Update your build.gradle.kts to use the latest version:

```kotlin
dependencies {
    // Update to latest version
    implementation("com.github.charliepank:blockchain-relay-utility:v0.0.8")
    
    // Remove old blockchain configuration dependencies if any
    // The utility now provides all necessary blockchain configs
}
```

#### 3. Remove Conflicting Configuration
Remove any duplicate Web3j or blockchain configuration beans:

```kotlin
// ❌ Remove these - provided by the utility
@Configuration
class Web3Config {
    @Bean
    fun web3j(): Web3j = Web3j.build(HttpService(rpcUrl))  // Conflicts!
    
    @Bean 
    fun chainId(): Long = 43113  // Conflicts!
}
```

#### 4. Migrate to Plugin Pattern
Convert your business logic to a plugin:

```kotlin
import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.plugin.BlockchainServicePlugin
import org.springframework.stereotype.Component

@Component
class MyServicePlugin(
    private val myTransactionService: MyTransactionService
) : BlockchainServicePlugin {
    
    override fun getPluginName(): String = "my-service"
    override fun getApiPrefix(): String = "/api/chain"  // Keep existing API paths
    
    override fun initialize(relayService: BlockchainRelayService, authProvider: AuthenticationProvider) {
        // Initialize with utility services
        myTransactionService.initialize(relayService, authProvider)
    }
    
    // ... rest of plugin implementation
}
```

### Configuration Property Migration

**Before (Conflicting):**
```yaml
blockchain:
  rpcUrl: "${RPC_URL}"
  contractAddress: "${CONTRACT_ADDRESS}"  # ❌ Will conflict
  creatorFee: 1000000                    # ❌ Will conflict
```

**After (Correct):**
```yaml
# Core blockchain properties (provided by utility)
blockchain:
  rpcUrl: "${RPC_URL}"
  chainId: 43113
  relayer:
    gasPayerContractAddress: "${GAS_PAYER_CONTRACT_ADDRESS}"

# Your business properties (separate prefix)
my-service:
  contractAddress: "${CONTRACT_ADDRESS}"
  creatorFee: 1000000
```

## Troubleshooting

### Common Issues and Solutions

#### 1. Suspend Function Compilation Errors
```
e: Suspend function 'relayTransaction' should be called only from a coroutine or another suspend function
e: Unresolved reference: runBlocking
```

**Cause**: Missing coroutines dependency or incorrect usage of suspend functions.

**Solution**: 
1. Add kotlinx-coroutines dependency:
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
```
2. Import and use `runBlocking`:
```kotlin
import kotlinx.coroutines.runBlocking

val result = runBlocking {
    blockchainService.relayTransaction(hex)
}
```

#### 2. Incorrect TransactionResult Import
```
e: Unresolved reference: TransactionResult
```

**Cause**: Importing from wrong package path.

**Solution**: Use the correct import:
```kotlin
import com.utility.chainservice.models.TransactionResult  // ✅ Correct
// NOT: import com.utility.chainservice.TransactionResult  // ❌ Wrong
```

#### 3. Test Mocking Errors with Suspend Functions
```
e: Suspend function 'relayTransaction' should be called only from a coroutine
```

**Cause**: Using `every` instead of `coEvery` for mocking suspend functions.

**Solution**: Use `coEvery` from MockK:
```kotlin
import io.mockk.coEvery

coEvery { blockchainService.relayTransaction(any()) } returns result  // ✅ Correct
// NOT: every { blockchainService.relayTransaction(any()) } returns result  // ❌ Wrong
```

#### 4. Ambiguous Mapping Errors
```
Ambiguous mapping. Cannot map 'myController' method to {POST [/api/chain/operation]}: 
There is already 'pluginController' bean method mapped.
```

**Cause**: Duplicate controller endpoints between your legacy controllers and the plugin system.

**Solution**: Remove legacy controllers that implement the same endpoints as your plugin.

#### 2. Bean Definition Override Errors
```
Invalid bean definition with name 'web3j': Cannot register bean definition... 
since there is already [...] bound.
```

**Cause**: Your application defines beans that conflict with the utility's auto-configuration.

**Solution**: Remove duplicate bean definitions from your configuration classes.

#### 3. Configuration Property Binding Errors
```
Failed to bind properties under 'blockchain' to com.yourapp.YourProperties
```

**Cause**: Using the reserved `blockchain` prefix for your business properties.

**Solution**: Use a different prefix for your `@ConfigurationProperties` classes.

#### 4. Plugin Not Found Errors
```
No blockchain service plugins found
```

**Cause**: Component scan not including the utility packages or plugin not properly annotated.

**Solution**: 
- Ensure `@ComponentScan` includes both your packages and `"com.utility.chainservice"`
- Verify your plugin class is annotated with `@Component`
- Check that your plugin implements `BlockchainServicePlugin`

#### 5. Authentication System Changes

The token-based authentication system has been removed. The library now uses only API key authentication with security configuration files. Remove any `AuthenticationProvider` or `HttpAuthenticationProvider` beans from your configuration.

### Debug Tips

1. **Enable debug logging** to see plugin initialization:
```yaml
logging:
  level:
    com.utility.chainservice.plugin: DEBUG
```

2. **Check Spring context** for bean conflicts:
```kotlin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

@Autowired
lateinit var applicationContext: ApplicationContext

fun debugBeans() {
    applicationContext.getBeanDefinitionNames()
        .filter { it.contains("web3j") }
        .forEach { println("Bean: $it") }
}
```

3. **Verify plugin registration**:
```
2025-07-30 20:13:37 [main] INFO  c.u.c.plugin.PluginConfiguration - Initializing 1 blockchain service plugins
2025-07-30 20:13:37 [main] INFO  c.u.c.plugin.PluginConfiguration - Successfully initialized plugin: my-service
```

## Examples

See the [examples directory](./examples) for complete plugin implementations:
- Escrow Service Plugin
- NFT Marketplace Plugin
- DEX Trading Plugin

## Contributing

1. Fork the repository
2. Create your feature branch
3. Add tests for your changes
4. Ensure all tests pass
5. Submit a pull request

## License

MIT License - see LICENSE file for details.