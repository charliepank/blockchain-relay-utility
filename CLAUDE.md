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

1. **Gas Management**: Automatically calculates exact gas costs from user-signed transactions and transfers AVAX to user wallets if insufficient balance
2. **Transaction Forwarding**: Forwards original signed transactions unchanged to the blockchain
3. **Receipt Waiting**: Monitors transaction status and provides success/failure results

## Development Commands

### Build and Test
```bash
./gradlew build                    # Build project
./gradlew test                     # Run tests
./gradlew bootRun                 # Run Spring Boot application
./gradlew clean                   # Clean build directory
```

### Publishing
```bash
./gradlew publishToMavenLocal     # Publish to local Maven repository
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
    price-multiplier: 1.2  # Multiply network gas price by this factor
    minimum-gas-price-wei: 6  # Minimum gas price in wei

auth:
  user-service-url: "${USER_SERVICE_URL}"
  enabled: true  # Set to false to disable authentication
```

## Key Features

### Gas Management
- Extracts exact gas costs from user-signed transactions (supports both legacy and EIP-1559)
- Automatically transfers AVAX to user wallets before transaction execution
- Only transfers the exact amount needed (gas cost + transaction value - current balance)

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

- Test configuration in `src/test/resources/application-test.yml`
- Uses Avalanche Fuji testnet (chain ID 43113) for testing
- Mock authentication disabled in test environment
- Tests use JUnit 5 with Mockito Kotlin

## Library Usage

This project is designed to be used as a dependency in other Spring Boot applications:

1. Add dependency to your `build.gradle.kts`
2. Configure application properties for blockchain and authentication
3. Implement business-specific plugins by extending `BlockchainServicePlugin`
4. Enable component scanning for `com.utility.chainservice` package
5. Create REST controllers that utilize the initialized plugins