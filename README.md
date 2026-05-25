# C4 Context Diagram - File Transfer and Processing System

This repository contains a C4 Context diagram illustrating the architecture of a file transfer and processing system using MFT, S3, Lambda, and RabbitMQ.

## Architecture Overview

The system follows this data flow:

1. **MFT (Managed File Transfer)**: Entry point for file uploads from external systems/users
2. **Amazon S3**: Cloud storage for uploaded files
3. **AWS Lambda**: Serverless processing triggered by S3 events
4. **RabbitMQ**: Message broker for asynchronous message delivery to downstream systems

## Components

### External Actors
- **External System/User**: Initiates file transfers via MFT using protocols like SFTP/FTPS

### System Components
- **Managed File Transfer (MFT)**: Secure file transfer gateway that receives files from external sources
- **Amazon S3**: Object storage service that stores uploaded files and triggers processing events
- **AWS Lambda**: Serverless compute service that processes files and transforms them into messages
- **RabbitMQ**: Message broker that queues and delivers messages to downstream consumers

### External Systems
- **Downstream Systems**: Applications that consume processed messages from RabbitMQ

## Data Flow

```
External User/System → MFT → S3 → Lambda → RabbitMQ → Downstream Systems
```

1. External systems upload files to MFT via SFTP/FTPS
2. MFT stores files in Amazon S3 using S3 API
3. S3 triggers Lambda function via S3 Event Notification
4. Lambda processes the file and publishes messages to RabbitMQ via AMQP
5. RabbitMQ delivers messages to downstream systems via AMQP

## Viewing the Diagram

### Prerequisites
- PlantUML installed locally, OR
- Use an online PlantUML viewer, OR
- VS Code with PlantUML extension

### Option 1: VS Code (Recommended)
1. Install the "PlantUML" extension by jebbs
2. Open `c4-context-diagram.puml`
3. Press `Alt+D` (or `Option+D` on Mac) to preview the diagram

### Option 2: Online Viewer
1. Visit [PlantUML Online Server](http://www.plantuml.com/plantuml/uml/)
2. Copy the contents of `c4-context-diagram.puml`
3. Paste into the editor to view the diagram

### Option 3: Command Line
```bash
# Install PlantUML (requires Java)
# On macOS with Homebrew:
brew install plantuml

# Generate PNG image
plantuml c4-context-diagram.puml

# Generate SVG image
plantuml -tsvg c4-context-diagram.puml
```

## Files

- `c4-context-diagram.puml`: PlantUML source file for the C4 context diagram
- `README.md`: This documentation file

## C4 Model

This diagram follows the C4 model for visualizing software architecture:
- **Context**: Shows the system in its environment with external dependencies (this diagram)
- **Container**: Would show the internal containers/services (not included)
- **Component**: Would show internal components of containers (not included)
- **Code**: Would show class diagrams (not included)

## Customization

To modify the diagram:
1. Edit `c4-context-diagram.puml`
2. Update component names, relationships, or add new elements
3. Regenerate the diagram using one of the viewing methods above

## Use Cases

This architecture pattern is suitable for:
- Secure file transfer and processing workflows
- ETL (Extract, Transform, Load) pipelines
- Event-driven file processing systems
- Asynchronous message-based integrations
- Decoupled microservices architectures

## Benefits

- **Security**: MFT provides secure file transfer protocols
- **Scalability**: S3 and Lambda scale automatically
- **Reliability**: RabbitMQ ensures message delivery
- **Decoupling**: Components are loosely coupled via events and messages
- **Cost-effective**: Pay-per-use model with serverless components

## License

This diagram is provided as-is for documentation purposes.