# RabbitMQ Docker Manager for Inference Service

This project contains the source code for managing Docker containers via RabbitMQ messaging to perform tasks related to an inference service. The main components include the management of Docker containers through RabbitMQ messages, Redis for data persistence, and monitoring containers' health and status.

## Features

- **RabbitMQ Integration**: Utilizes RabbitMQ for message passing and queue management.
- **Docker Container Management**: Handles Docker commands for starting, stopping, and monitoring containers.
- **Redis Integration**: Uses Redis for storing and retrieving data such as timestamps for each container.
- **Dynamic Queue Management**: Dynamically handles queues and exchange bindings within RabbitMQ.

## Prerequisites

- Docker
- Docker Compose
- Java 11
- RabbitMQ
- Redis

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Installation

1. **Clone the repository:**

```bash
   git clone https://github.com/yourusername/rabbitmq-docker-manager.git
```

## Build the Docker image:

```bash
docker-compose build
``` 

## Run the services:

```bash
docker-compose up
```

## Configuration
The system's behavior can be configured through environment variables:

- RABBITMQ_HOST: The hostname of the RabbitMQ server.
- RABBITMQ_PORT: The port on which RabbitMQ is running.
- RABBITMQ_USER: Username for RabbitMQ authentication.
- RABBITMQ_PASS: Password for RabbitMQ authentication.
These are set in the docker-compose.yml file and can be modified according to your environment.

## Usage
Start the service by running the Docker containers. The RabbitMQ Docker Manager will automatically connect to the configured RabbitMQ and Redis instances and start listening for messages on the configured queues.

## License
This project is licensed under the MIT License - see the LICENSE.md file for details.