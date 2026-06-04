# 🎓 College FAQ Assistant – RAG Powered AI Chatbot

A Retrieval-Augmented Generation (RAG) based chatbot that allows students to ask questions about college information directly from uploaded documents such as handbooks, academic regulations, fee structures, examination guidelines, and academic calendars.

The system combines modern web development, vector databases, embeddings, and Large Language Models (LLMs) to provide accurate, context-aware answers grounded in institutional documents.

---

# 🚀 Features

## 📄 Document Management

* Upload PDF documents
* Upload DOCX documents
* Automatic document processing
* Document status tracking
* Chunk generation and indexing
* Document metadata management

## 🤖 AI-Powered Question Answering

* Natural language question answering
* Context-aware responses
* Retrieval-Augmented Generation (RAG)
* Semantic search using embeddings
* Multi-document knowledge retrieval
* Hallucination reduction through document grounding

## 💬 Chat System

* Session-based conversations
* Message history tracking
* Source document tracking
* Feedback collection system
* Persistent chat storage

## ⚡ Modern Architecture

* React Frontend
* Spring Boot Backend
* PostgreSQL Database
* ChromaDB Vector Database
* Ollama Local AI Models
* Dockerized Services

---

# 🏗️ System Architecture

```text
                    Student
                       │
                       ▼
                React Frontend
                       │
                  REST APIs
                       │
                       ▼
                Spring Boot API
                       │
      ┌────────────────┼────────────────┐
      ▼                ▼                ▼
 PostgreSQL        ChromaDB         Ollama
(Relational)       (Vectors)      (AI Models)
```

---

# 🧠 What is RAG?

This project uses Retrieval-Augmented Generation (RAG).

Instead of directly asking an LLM to answer a question, the system first retrieves relevant information from uploaded college documents and then uses that information to generate an answer.

## Traditional Chatbot

```text
Question
   ↓
LLM
   ↓
Answer
```

Problem:

* Hallucinations
* No knowledge of college documents
* Inaccurate answers

## RAG Chatbot

```text
Question
   ↓
Retrieve Relevant Content
   ↓
Augment Prompt
   ↓
Generate Answer
```

Benefits:

* More accurate responses
* Reduced hallucinations
* Answers grounded in official documents
* Institution-specific knowledge

---

# 🔄 Document Processing Pipeline

When an administrator uploads a document, the following pipeline executes:

```text
PDF / DOCX Upload
         ↓
Text Extraction
         ↓
Chunking
         ↓
Embedding Generation
         ↓
Vector Storage
         ↓
ChromaDB
```

## Step 1: Text Extraction

Documents are processed using:

* Apache PDFBox (PDF)
* Apache POI (DOCX)

## Step 2: Chunking

Large documents are split into smaller chunks.

Example:

```text
Chunk 1 → Attendance Policy
Chunk 2 → Examination Rules
Chunk 3 → Fee Structure
```

Chunking improves retrieval quality and reduces context size.

## Step 3: Embedding Generation

Each chunk is converted into a vector representation using:

```text
nomic-embed-text
```

Example:

```text
Attendance Requirement
        ↓
[0.12, -0.45, 0.91, ...]
```

## Step 4: Vector Storage

Embeddings are stored inside ChromaDB for semantic similarity search.

---

# 💬 Question Answering Pipeline

When a student asks a question:

```text
Student Question
        ↓
Generate Query Embedding
        ↓
Similarity Search
        ↓
Retrieve Relevant Chunks
        ↓
Prompt Augmentation
        ↓
LLM Generation
        ↓
Answer
```

Example:

Question:

```text
What is the minimum attendance requirement?
```

Retrieved Context:

```text
Attendance should be maintained at 75%.
```

Generated Answer:

```text
The minimum attendance requirement is 75%
according to the uploaded college document.
```

---

# 🛠️ Technology Stack

| Layer            | Technology       |
| ---------------- | ---------------- |
| Frontend         | React            |
| Backend          | Spring Boot      |
| Database         | PostgreSQL       |
| Vector Database  | ChromaDB         |
| Containerization | Docker           |
| LLM Runtime      | Ollama           |
| Chat Model       | Gemma 3          |
| Embedding Model  | Nomic Embed Text |
| Build Tool       | Maven            |
| ORM              | Hibernate / JPA  |
| API Style        | REST             |

---

# 📂 Project Structure

```text
college-faq-assistant
│
├── frontend/
│   ├── src/
│   ├── public/
│   └── package.json
│
├── src/main/java/com/example/demo
│
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── model/
│   ├── dto/
│   ├── config/
│   └── exception/
│
├── src/main/resources
│   └── application.properties
│
├── pom.xml
└── README.md
```

---

# 🗄️ Database Design

## Main Entities

### Document

Stores uploaded document metadata.

```text
id
fileName
fileType
status
uploadedAt
uploadedBy
totalChunks
```

### DocumentChunk

Stores document chunks.

```text
id
chunkText
chunkIndex
chromaId
documentId
```

### ChatSession

Stores conversation sessions.

```text
id
sessionId
createdAt
```

### Message

Stores user and assistant messages.

```text
id
sessionId
role
content
createdAt
```

### FeedbackLog

Stores answer ratings.

```text
id
messageId
rating
```

---

# 🔗 Entity Relationships

```text
Document
   │
   └──────< DocumentChunk

ChatSession
   │
   └──────< Message

Message
   │
   └──────< FeedbackLog
```

---

# 📡 API Endpoints

## Upload Document

```http
POST /api/admin/documents
```

Request:

```multipart
file=document.pdf
```

Response:

```json
{
  "id": 1,
  "status": "COMPLETED"
}
```

---

## List Documents

```http
GET /api/admin/documents
```

---

## Ask Question

```http
POST /api/chat
```

Request:

```json
{
  "sessionId":"abc123",
  "question":"What is the attendance requirement?"
}
```

Response:

```json
{
  "answer":"Students must maintain 75% attendance.",
  "sources":[
      "College Handbook.pdf"
  ]
}
```

---

## Chat History

```http
GET /api/chat/history/{sessionId}
```

---

## Submit Feedback

```http
POST /api/chat/feedback
```

Request:

```json
{
  "messageId": 1,
  "rating": "THUMBS_UP"
}
```

---

# ⚙️ Local Setup

## Clone Repository

```bash
git clone https://github.com/your-username/college-faq-assistant.git

cd college-faq-assistant
```

---

# PostgreSQL Setup

Create database:

```sql
CREATE DATABASE college_chatbot;
```

Update:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/college_chatbot
spring.datasource.username=postgres
spring.datasource.password=your_password
```

---

# ChromaDB Setup

Pull image:

```bash
docker pull chromadb/chroma
```

Run container:

```bash
docker run -d --name chromadb -p 8000:8000 chromadb/chroma
```

Verify:

```bash
docker ps
```

---

# Ollama Setup

Install Ollama.

Pull models:

```bash
ollama pull gemma3:4b
```

```bash
ollama pull nomic-embed-text
```

Verify:

```bash
ollama list
```

---

# Backend Setup

```bash
mvn clean install
```

```bash
mvn spring-boot:run
```

Backend runs on:

```text
http://localhost:8080
```

---

# Frontend Setup

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Run:

```bash
npm run dev
```

Frontend runs on:

```text
http://localhost:5173
```

---

# 🧪 Testing Workflow

## Document Upload

1. Open Admin Panel
2. Upload PDF/DOCX
3. Wait for processing
4. Verify status becomes COMPLETED

## Chat Testing

Ask:

```text
What is the attendance requirement?
```

Expected:

```text
Answer generated using uploaded documents.
```

---

# 🔥 Challenges Solved

* PDF and DOCX parsing
* Semantic chunking
* Embedding generation
* Vector database integration
* ChromaDB communication
* Ollama integration
* Prompt engineering
* RAG implementation
* Spring Boot architecture
* Frontend-backend communication

---

# 🚀 Future Enhancements

* Authentication & Authorization
* JWT Security
* Role-Based Access Control
* Hybrid Search (BM25 + Vector Search)
* Source Citation with Page Numbers
* Multi-language Support
* Cloud Deployment
* Conversation Memory
* Analytics Dashboard
* Fine-Tuned Institutional Models

---

# 📚 Learning Outcomes

Through this project, the following concepts were implemented and explored:

* Retrieval-Augmented Generation (RAG)
* Vector Databases
* Embeddings
* Semantic Search
* Spring Boot Architecture
* REST APIs
* JPA/Hibernate
* PostgreSQL
* Docker
* ChromaDB
* Ollama
* Large Language Models
* Prompt Engineering
* Software Design Principles
* Full Stack Development

---

# 👨‍💻 Author

Gufran Ahmed

Computer Science and Engineering Student

Interested in:

* Java Backend Development
* Machine Learning
* Artificial Intelligence
* Data Science
* System Design

---

# 📄 License

This project is intended for educational and research purposes.
Feel free to fork, learn from, and contribute to the project.
