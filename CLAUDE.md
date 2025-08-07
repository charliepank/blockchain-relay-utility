# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **generic Kotlin/Spring Boot utility for blockchain transaction relaying** with gas management and pluggable business logic. The library provides core blockchain interaction capabilities while allowing business-specific functionality to be added through a plugin system.

## Architecture

### Core Components

- **BlockchainRelayService** (`src/main/kotlin/com/utility/chainservice/BlockchainRelayService.kt`): Main service handling transaction relaying, gas transfers, and blockchain operations using Web3j
- **BlockchainServicePlugin** (`src/main/kotlin/com/utility/chainservice/plugin/BlockchainServicePlugin.kt`): Interface for implementing business-specific plugins
- **PluginConfiguration** (`src/main/kotlin/com/utility/chainservice/plugin/PluginConfiguration.kt`): Auto-discovery and initialization of plugins
- **AuthenticationProvider** (`src/main/kotlin/com/utility/chainservice/AuthenticationProvider.kt`): Interface for pluggable authentication systems
- **UtilityAutoConfiguration** (`src/main/kotlin/com/utility/chainservice/UtilityAutoConfiguration.kt`): Spring Boot auto-configuration with blockchain and authentication setup

### Plugin System

The utility implements a plugin architecture where:
- **Generic Utility**: Core blockchain operations, gas management, authentication
- **Business Plugins**: Implement `BlockchainServicePlugin` interface to add domain-specific functionality
- **Auto-Discovery**: Plugins are automatically detected and initialized via Spring component scanning

### Transaction Flow

1. **Transaction Decoding**: Decodes user's signed transaction to extract gas limit, value, and contract data
2. **Gas Validation**: Calls `ethEstimateGas` on-chain to validate user's gas limit is reasonable (within tolerance)
3. **Balance Check & Funding**: Checks user's AVAX balance and transfers exact amount needed if insufficient
4. **Transaction Forwarding**: Forwards original signed transaction unchanged to the blockchain
5. **Receipt Waiting**: Monitors transaction status and provides success/failure results

## Development Commands

### Build and Test
```bash
./gradlew build                    # Build project with tests and jar
./gradlew test                     # Run tests only
./gradlew test jacocoTestReport   # Run tests with coverage report
./gradlew bootRun                 # Run Spring Boot application (library mode)
./gradlew clean                   # Clean build directory
```

### Single Test Execution
```bash
./gradlew test --tests "com.utility.chainservice.BlockchainRelayServiceTest"
./gradlew test --tests "*RelayServiceTest*"
./gradlew test --tests "*.shouldCalculateOperationGasCosts*"
```

### Publishing and Distribution
```bash
./gradlew publishToMavenLocal     # Publish to local Maven repository
./gradlew jar                     # Create JAR artifact (bootJar disabled)
```

## Configuration

### Required Environment Variables
- `RPC_URL`: Blockchain RPC endpoint (e.g., Avalanche network)
- `RELAYER_PRIVATE_KEY`: Private key for gas-paying wallet (0x-prefixed hex, 66 chars)
- `RELAYER_WALLET_ADDRESS`: Address of the relayer wallet
- `USER_SERVICE_URL`: URL for user authentication service (if using HTTP auth)

### Application Configuration (application.yml)
```yaml
blockchain:
  rpc-url: "${RPC_URL}"
  chain-id: 43113  # Auto-detected from RPC if not specified
  relayer:
    private-key: "${RELAYER_PRIVATE_KEY}"
    wallet-address: "${RELAYER_WALLET_ADDRESS}"
  gas:
    price-multiplier: 1.2  # Multiply network gas price by this factor (default: 1.2)
    minimum-gas-price-wei: 6  # Minimum gas price in wei (default: 6)
    validation-tolerance-percent: 50  # Allow up to 50% more gas than estimate (default: 50)
    max-gas-cost-wei: 540000000  # Maximum total cost gasLimit*gasPrice in wei (default: ~$0.014)

auth:
  user-service-url: "${USER_SERVICE_URL}"
  enabled: true  # Set to false to disable authentication
```

## Key Features

### Gas Management
- **Security-First Gas Validation**: Validates user-provided gas limits against on-chain estimates before funding
- Extracts exact gas costs from user-signed transactions (supports both legacy and EIP-1559)
- Automatically transfers AVAX to user wallets before transaction execution
- Only transfers the exact amount needed (gas cost + transaction value - current balance)
- Configurable tolerance threshold prevents excessive gas limit abuse

### Authentication System
- Pluggable authentication via `AuthenticationProvider` interface
- Default HTTP-based authentication using external user service
- JWT token validation with reactive (Mono) responses

### Plugin Development
To create a plugin:
1. Implement `BlockchainServicePlugin` interface
2. Define plugin name, API prefix, and OpenAPI documentation
3. Implement `initialize()` method to access relay service and auth provider
4. Define gas operations for cost estimation
5. Create REST controllers using the plugin's API prefix

### Web3j Integration
- Full Ethereum-compatible blockchain support
- Transaction decoding and encoding
- Gas estimation and price calculation
- Receipt monitoring with configurable retry logic

## Testing

### Test Configuration
- Test configuration in `src/test/resources/application-test.yml`
- Uses Avalanche Fuji testnet (chain ID 43113) for testing
- Mock authentication disabled in test environment (`auth.enabled: false`)
- Debug logging enabled for `com.utility.chainservice` package

### Testing Framework
- **JUnit 5** with `@Test` annotations
- **Mockito Kotlin** for mocking with `mock()`, `whenever()`, `verify()`
- **Coroutines Testing** with `runBlocking` for suspend functions
- **Spring Boot Test** with `@SpringBootTest` for integration tests

### Test Patterns
- **Suspend Function Testing**: Use `runBlocking { }` to test suspend methods
- **Web3j Mocking**: Mock `Request<*, ResponseType>` objects and their `.send()` methods
- **Exception Testing**: Test error scenarios with `assertFalse(result.success)` and `assertNotNull(result.error)`
- **Coverage Reports**: Generated in `build/reports/jacoco/test/html/index.html`

## Library Usage

This project is designed to be used as a dependency in other Spring Boot applications:

1. Add dependency to your `build.gradle.kts`
2. Configure application properties for blockchain and authentication
3. Implement business-specific plugins by extending `BlockchainServicePlugin`
4. Enable component scanning for `com.utility.chainservice` package
5. Create REST controllers that utilize the initialized plugins

### Important Implementation Notes
- This is a **library project** (not standalone application) - `bootJar` is disabled, `jar` is enabled
- Uses **Spring Boot auto-configuration** via `UtilityAutoConfiguration.kt`
- **Plugin auto-discovery** happens through Spring component scanning
- All blockchain operations are **suspend functions** requiring coroutines support
- **JitPack publishing** is configured for easy distribution as GitHub dependency