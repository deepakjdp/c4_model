# C4 Context Diagram - File Transfer and Processing System

This repository contains C4 Context diagrams for a file transfer and processing system using **MFT → S3 → Lambda → RabbitMQ** architecture.

## 📊 View the Diagram

**Primary (GitHub Native):** [c4-context-diagram.md](c4-context-diagram.md) - Uses Mermaid, renders directly on GitHub

**Alternative (PlantUML):** [c4-context-diagram.puml](c4-context-diagram.puml) - Requires PlantUML viewer

## 🏗️ Architecture Overview

```
External User/System → MFT → S3 → Lambda → RabbitMQ → Downstream Systems
```

### Components

1. **MFT (Managed File Transfer)**: Secure entry point for file uploads (SFTP/FTPS)
2. **Amazon S3**: Cloud object storage for uploaded files
3. **AWS Lambda**: Serverless processing triggered by S3 events
4. **RabbitMQ**: Message broker for asynchronous delivery to downstream systems

## 🚀 Quick Start

### View on GitHub
Simply open [c4-context-diagram.md](c4-context-diagram.md) on GitHub - the Mermaid diagram will render automatically!

### View Locally in VS Code
1. Install the "Markdown Preview Mermaid Support" extension
2. Open `c4-context-diagram.md`
3. Press `Cmd+Shift+V` (Mac) or `Ctrl+Shift+V` (Windows/Linux) for preview

### View PlantUML Version
1. Install "PlantUML" extension in VS Code
2. Open `c4-context-diagram.puml`
3. Press `Alt+D` (or `Option+D` on Mac) to preview

## 📁 Files

- **c4-context-diagram.md** - Mermaid diagram (GitHub-friendly)
- **c4-context-diagram.puml** - PlantUML diagram (requires viewer)
- **README.md** - This documentation

## 💡 Use Cases

This architecture pattern is ideal for:
- Secure file transfer and processing workflows
- ETL (Extract, Transform, Load) pipelines
- Event-driven file processing systems
- Asynchronous message-based integrations
- Decoupled microservices architectures

## ✨ Benefits

- **Security**: MFT provides secure file transfer protocols
- **Scalability**: S3 and Lambda scale automatically
- **Reliability**: RabbitMQ ensures guaranteed message delivery
- **Decoupling**: Components are loosely coupled via events and messages
- **Cost-effective**: Pay-per-use model with serverless components

## 📖 About C4 Model

This is a **Context diagram** (Level 1 of the C4 model), showing:
- The system boundary
- External actors and systems
- High-level relationships and data flow

For more about C4 model: https://c4model.com/