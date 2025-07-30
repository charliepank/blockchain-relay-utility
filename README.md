# Blockchain Transaction Relay Utility

A generic Kotlin/Spring Boot utility for blockchain transaction relaying with gas management and pluggable business logic.

## Features

- **Generic Transaction Relaying**: Core blockchain interaction and transaction forwarding
- **Gas Management**: Automatic gas transfer to user wallets before transaction execution
- **Authentication Interface**: Pluggable authentication providers (HTTP, JWT, etc.)
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
    implementation("com.github.charliep:blockchain-relay-utility:main-SNAPSHOT")
    // Or use a specific release tag:
    // implementation("com.github.charliep:blockchain-relay-utility:v1.0.0")
    
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
    privateKey: "0x..." # Your relayer wallet private key
    walletAddress: "0x..." # Your relayer wallet address
  gas:
    priceMultiplier: 1.2
    minimumGasPriceWei: 6

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
@RestController
@RequestMapping("/api/my-service")
class MyServiceController(
    private val myServicePlugin: MyServicePlugin
) {
    
    @PostMapping("/my-operation")
    fun myOperation(@RequestBody request: MyRequest): ResponseEntity<MyResponse> {
        // Use myServicePlugin.getRelayService() for blockchain operations
        // Use myServicePlugin.getAuthProvider() for authentication
    }
}
```

### 5. Enable Component Scan

```kotlin
@SpringBootApplication
@ComponentScan(basePackages = ["com.yourcompany.yourapp", "com.utility.chainservice"])
class YourApplication
```

## Generic Endpoints

The utility provides these generic endpoints:

- `GET /api/relay/health` - Service health check with plugin status
- `GET /api/relay/gas-costs` - Current gas costs for all plugin operations

## Architecture

### Core Components

- **BlockchainRelayService**: Core transaction relaying and gas management
- **AuthenticationProvider**: Interface for user authentication systems
- **HttpAuthenticationProvider**: HTTP-based authentication implementation
- **RelayController**: Generic utility REST endpoints
- **PluginConfiguration**: Auto-discovery and initialization of plugins

### Plugin Interface

Implement `BlockchainServicePlugin` to add your business logic:

```kotlin
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
- `RELAYER_PRIVATE_KEY` - Private key for gas-paying wallet (0x-prefixed hex)
- `RELAYER_WALLET_ADDRESS` - Address of the relayer wallet
- `USER_SERVICE_URL` - URL for user authentication service (if using HTTP auth)

### Property Configuration

The utility uses `@ConfigurationProperties(prefix = "blockchain")` for core blockchain settings. **Important**: Only use the `blockchain` prefix for utility-provided properties. For your business-specific configuration, create separate `@ConfigurationProperties` classes with different prefixes.

```yaml
# ✅ Correct - utility properties
blockchain:
  rpcUrl: "${RPC_URL}"
  chainId: 43113  # Default: auto-detected from RPC
  relayer:
    privateKey: "${RELAYER_PRIVATE_KEY}"
    walletAddress: "${RELAYER_WALLET_ADDRESS}"
  gas:
    priceMultiplier: 1.2  # Default: 1.2x network gas price
    minimumGasPriceWei: 6  # Default: 6 wei minimum

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
@ConfigurationProperties(prefix = "my-service")
data class MyServiceProperties(
    var contractAddress: String = "",
    var feeAmount: BigInteger = BigInteger.ZERO
)
```

## Authentication

The utility supports pluggable authentication:

### HTTP Authentication (Default)

```kotlin
@Bean
fun authenticationProvider(): AuthenticationProvider {
    return HttpAuthenticationProvider(
        userServiceUrl = "https://your-user-service.com",
        enabled = true
    )
}
```

### Custom Authentication

```kotlin
@Component
class CustomAuthProvider : AuthenticationProvider {
    override fun validateToken(authToken: String, httpOnlyToken: String?): Mono<AuthenticationResult> {
        // Your custom authentication logic
    }
    
    override fun isAuthEnabled(): Boolean = true
}
```

## Gas Management

The utility automatically:
1. Calculates exact gas costs from user-signed transactions
2. Transfers AVAX to user wallets if insufficient balance
3. Forwards original signed transactions unchanged
4. Handles both legacy and EIP-1559 transactions

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
    privateKey: "${RELAYER_PRIVATE_KEY}"
    walletAddress: "${RELAYER_WALLET_ADDRESS}"

# Your business properties (separate prefix)
my-service:
  contractAddress: "${CONTRACT_ADDRESS}"
  creatorFee: 1000000
```

## Troubleshooting

### Common Issues and Solutions

#### 1. Ambiguous Mapping Errors
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

#### 5. Authentication Integration Issues
```
AuthenticationProvider bean not found
```

**Cause**: No authentication provider configured or conflicting authentication setup.

**Solution**: Either configure the default HTTP authentication provider or implement a custom one:

```kotlin
@Bean  
fun authenticationProvider(): AuthenticationProvider {
    return HttpAuthenticationProvider(userServiceUrl, enabled = true)
}
```

### Debug Tips

1. **Enable debug logging** to see plugin initialization:
```yaml
logging:
  level:
    com.utility.chainservice.plugin: DEBUG
```

2. **Check Spring context** for bean conflicts:
```kotlin
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