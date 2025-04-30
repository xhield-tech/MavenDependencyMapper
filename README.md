# MavenDependencyMapper

A Spring Boot REST API service that analyzes Maven POM files to generate comprehensive dependency trees, revealing both direct and transitive dependencies. Simplify dependency management and gain insights into your project's complete dependency structure.

## Features
- Parse POM files to extract dependency information
- Identify all direct dependencies
- Recursively discover transitive dependencies
- RESTful API for integration with other tools
- Detailed dependency reporting

## Getting Started

### Prerequisites
- Java 11 or higher
- Maven 3.6+

### Installation

Clone the repository:
```bash
git clone https://github.com/yourusername/MavenDependencyMapper.git
cd MavenDependencyMapper
```
Install dependencies:
```mvn clean install```
Running the Application
Start the Spring Boot application:
```mvn spring-boot:run```
The API will be available at http://localhost:8080
