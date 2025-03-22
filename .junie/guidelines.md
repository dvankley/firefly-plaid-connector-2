# Firefly Plaid Connector 2 - Project Guidelines

## Project Overview

Firefly Plaid Connector 2 is a utility that connects the Plaid financial data aggregation service to the Firefly III personal finance management tool. The connector pulls transaction data from financial institutions via Plaid's API and imports it into Firefly III, allowing users to track and manage their finances in one place.

### Key Features

- **Dual Operation Modes**:
  - **Batch Mode**: For historical data backfilling, pulling transactions from a specified date range
  - **Polled Mode**: For ongoing synchronization, periodically checking for new transactions

- **Smart Transfer Matching**: Identifies transactions that represent transfers between accounts and converts them to Firefly's transfer transaction type

- **Category Mapping**: Maps Plaid's transaction categories to Firefly's budgets and categories

- **Deployment Options**: Can be run directly as a JAR or via Docker

## Technical Stack

- **Language**: Kotlin
- **Framework**: Spring Boot
- **Build System**: Gradle
- **APIs**: Plaid API, Firefly III API
- **Deployment**: Docker support with provided compose files

## Project Structure

- **src/main/kotlin**: Main source code
  - **api**: API client implementations for Plaid and Firefly
  - **sync**: Synchronization logic for batch and polled modes
  - **transactions**: Transaction processing and conversion logic

- **src/test/kotlin**: Test code
  - Contains unit tests for various components

- **specs**: API specifications
  - Contains Plaid API specification

## Development Guidelines

1. **Configuration**: Use application.yml for configuration, with environment-specific profiles as needed

2. **Testing**: Write unit tests for new functionality, especially for transaction conversion logic

3. **Error Handling**: Implement robust error handling, particularly for API interactions

4. **Logging**: Use appropriate logging levels to facilitate troubleshooting

5. **Documentation**: Update README.md when adding new features or changing existing functionality

## Contribution Workflow

1. Fork the repository
2. Create a feature branch
3. Implement changes with appropriate tests
4. Submit a pull request with a clear description of changes

## Resources

- [README.md](../README.md): Detailed documentation on setup, configuration, and usage
- [CONTRIBUTING.md](../CONTRIBUTING.md): Guidelines for contributing to the project