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

```yaml
# application.yml
blockchain:
  rpc-url: "https://api.avax-test.network/ext/bc/C/rpc"
  relayer:
    private-key: "0x..." # Your relayer wallet private key
    wallet-address: "0x..." # Your relayer wallet address
  gas:
    price-multiplier: 1.2
    minimum-gas-price-wei: 6

auth:
  user-service-url: "https://your-user-service.com"
  enabled: true
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

### Optional Configuration

```yaml
blockchain:
  chain-id: 43113  # Default: auto-detected from RPC
  gas:
    price-multiplier: 1.2  # Default: 1.2x network gas price
    minimum-gas-price-wei: 6  # Default: 6 wei minimum
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