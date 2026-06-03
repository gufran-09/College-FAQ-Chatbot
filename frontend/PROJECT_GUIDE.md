# College FAQ Chatbot Project Guide

This file explains the complete project from the frontend perspective so a new developer can understand:

- what the system does
- which services it depends on
- how to run each part
- how the frontend, backend, database, and AI services connect
- how the main features work end to end

## 1. Project Overview

This is a college FAQ and document assistant.

The user can:

- ask questions about college-related documents
- upload official PDF or DOCX files through the admin panel
- view indexed documents and their processing status
- send feedback on assistant answers

The system is split into three main layers:

- `frontend/` - React + Vite user interface
- `demo/` backend - Spring Boot REST API
- external services - PostgreSQL, ChromaDB, and Ollama

Important note:

- There is no Hugging Face integration in the current codebase.
- The AI/embedding service used by the backend is Ollama, not Hugging Face.

## 2. High-Level Architecture

The request flow looks like this:

```text
User in React frontend
  -> calls Spring Boot REST API
  -> backend stores data in PostgreSQL
  -> backend sends document chunks to ChromaDB for vector search
  -> backend calls Ollama for embeddings and answer generation
  -> frontend displays the final answer, sources, and admin status
```

## 3. Main Technologies

- Frontend: React 18, Vite, React Router, Axios, Lucide React
- Backend: Spring Boot 3, Spring Web, Spring Data JPA, Lombok
- Database: PostgreSQL
- Vector database: ChromaDB
- AI service: Ollama with `gemma3:4b` for chat and `nomic-embed-text` for embeddings
- File parsing: Apache PDFBox for PDF, Apache POI for DOCX

## 4. Folder Roles

### Frontend

The frontend lives in `demo/frontend/` and contains:

- `src/main.jsx` - app entry point
- `src/App.jsx` - top-level layout and routing
- `src/pages/ChatPage.jsx` - user chat screen
- `src/pages/AdminPage.jsx` - document upload and indexing screen
- `src/api.js` - Axios client configured with the backend base URL

### Backend

The backend lives in `demo/src/main/java/com/example/demo/` and contains:

- `controller/` - REST endpoints
- `service/` - document processing, retrieval, and AI logic
- `repository/` - JPA repositories for PostgreSQL
- `model/` - database entities

## 5. Services You Must Run

### PostgreSQL

PostgreSQL stores:

- chat sessions
- chat messages
- uploaded document records
- extracted document chunks
- user feedback

Default backend connection settings are read from `demo/src/main/resources/application.properties`.

Create the database before starting the backend:

```sql
CREATE DATABASE college_chatbot;
```

If your PostgreSQL password is not the same as the local default, set:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="your_password"
```

### ChromaDB

ChromaDB stores vectors for indexed document chunks.

Run it locally on port `8000`:

```powershell
docker run --rm -p 8000:8000 chromadb/chroma
```

### Ollama

Ollama is used for two things:

- embedding text into vectors
- generating the final assistant answer

Make sure Ollama is running locally before starting the backend:

```powershell
ollama pull gemma3:4b
ollama pull nomic-embed-text
```

The backend talks to Ollama on `http://localhost:11434` by default.

## 6. How To Run The Project

Run the services in this order.

### Step 1: Start PostgreSQL

Make sure PostgreSQL is running and the `college_chatbot` database exists.

If you prefer environment variables instead of hardcoded values, set:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/college_chatbot"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="your_password"
```

### Step 2: Start ChromaDB

Use Docker:

```powershell
docker run --rm -p 8000:8000 chromadb/chroma
```

If ChromaDB runs on a different URL, set:

```powershell
$env:CHROMADB_BASE_URL="http://localhost:8000"
```

### Step 3: Start the Spring Boot backend

Open a terminal in the `demo/` folder and run:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend default URL:

```text
http://localhost:8080
```

Backend API base URL:

```text
http://localhost:8080/api
```

### Step 4: Configure the frontend environment

Inside `demo/frontend/`, create or update `.env`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

If this variable is missing, the frontend still falls back to `http://localhost:8080/api`, but keeping it in `.env` is clearer.

### Step 5: Start the frontend

Open a terminal in `demo/frontend/` and run:

```powershell
npm install
npm run dev
```

Open the Vite URL, usually:

```text
http://localhost:5173/
```

Do not use `http://localhost:8080` for the UI. That port is only for the backend API.

## 7. How The Integration Works

### 7.1 Frontend to Backend

The frontend uses Axios in `src/api.js`:

```js
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
})
```

This means all frontend requests go to the Spring Boot API.

### 7.2 Chat Flow

The chat page in `src/pages/ChatPage.jsx` does this:

1. Generates or reuses a session ID from browser local storage.
2. Loads previous messages from `GET /api/chat/history/{sessionId}`.
3. Sends the user question to `POST /api/chat`.
4. Shows the assistant answer, sources, and feedback buttons.
5. Allows thumbs up/down feedback via `POST /api/chat/feedback`.

### 7.3 Admin Upload Flow

The admin page in `src/pages/AdminPage.jsx` does this:

1. Lets the admin upload a PDF or DOCX file.
2. Sends the file as `multipart/form-data` to `POST /api/admin/upload`.
3. Polls `GET /api/admin/documents` every five seconds.
4. Shows document status, total chunks, and upload metadata.
5. Deletes a document with `DELETE /api/admin/documents/{id}`.

## 8. Backend Processing Pipeline

When a file is uploaded, the backend runs an asynchronous ingestion pipeline.

### Step A: Save the document record

`IngestionService` creates a `Document` entry in PostgreSQL with status `PENDING`.

### Step B: Read the file

`FileReaderService` extracts text from:

- PDF using Apache PDFBox
- DOCX using Apache POI

### Step C: Split into chunks

`ChunkingService` divides the text into overlapping word chunks so the AI can search smaller pieces instead of one huge document.

### Step D: Create embeddings

`EmbeddingService` sends each chunk to the Ollama embedding model and gets a vector back.

### Step E: Store vectors in ChromaDB

`ChromaDbService` saves each chunk embedding in ChromaDB together with metadata such as:

- file name
- document ID
- chunk text

### Step F: Persist chunk metadata in PostgreSQL

The backend stores chunk records in PostgreSQL so documents can be tracked and deleted cleanly.

### Step G: Mark the document complete

Once all chunks are stored, the document status becomes `COMPLETED`.

If any step fails, the status becomes `FAILED`.

## 9. How Answer Generation Works

When the user asks a question:

1. The frontend sends the question and session ID to `POST /api/chat`.
2. `ChatService` stores the user message in PostgreSQL.
3. `EmbeddingService` converts the question into a vector using the Ollama embedding model.
4. `ChromaDbService` searches for the most similar document chunks.
5. `ChatService` builds a prompt from the retrieved chunks.
6. `LlmService` sends the prompt to the Ollama chat model.
7. The assistant answer is saved in PostgreSQL and returned to the frontend.

The answer is grounded in the uploaded documents, not free-form guessing.

## 10. Backend Endpoints Used By The Frontend

### Chat

- `POST /api/chat` - ask a question
- `GET /api/chat/history/{sessionId}` - load previous messages
- `POST /api/chat/feedback` - save thumbs up/down feedback

### Admin

- `POST /api/admin/upload` - upload a document
- `GET /api/admin/documents` - list all documents
- `GET /api/admin/documents/{id}` - check one document status
- `DELETE /api/admin/documents/{id}` - remove a document and its indexed chunks

## 11. Frontend Structure

### `App.jsx`

Defines the app shell, sidebar, routes, and layout.

Current routes:

- `/` - chat page
- `/admin` - admin panel
- `/documents` - placeholder page
- `/calendar` - placeholder page
- `/syllabus` - placeholder page

### `ChatPage.jsx`

This page handles:

- chat history loading
- new question submission
- assistant message rendering
- source display
- feedback submission

### `AdminPage.jsx`

This page handles:

- file selection
- document upload
- document polling
- document deletion
- showing document and chunk counts

## 12. Backend Structure

### Controllers

- `ChatController` exposes chat and feedback endpoints.
- `AdminController` exposes upload, list, status, and delete endpoints.

### Services

- `IngestionService` starts ingestion and deletes documents.
- `AsyncIngestionService` runs the ingestion pipeline in the background.
- `FileReaderService` extracts text from PDF/DOCX.
- `ChunkingService` splits text into overlapping chunks.
- `EmbeddingService` calls the Ollama embedding model.
- `ChromaDbService` stores and searches vectors.
- `LlmService` calls the Ollama chat model.
- `ChatService` handles question answering, history, and feedback.

### Repositories

JPA repositories store data in PostgreSQL for:

- documents
- document chunks
- chat sessions
- messages
- feedback logs

## 13. Environment Variables

These are the main variables you may need:

```env
VITE_API_BASE_URL=http://localhost:8080/api
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/college_chatbot
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
OLLAMA_BASE_URL=http://localhost:11434
CHROMADB_BASE_URL=http://localhost:8000
APP_CORS_ALLOWED_ORIGIN=http://localhost:5173
```

## 14. Practical Notes

- The admin panel polls the backend every five seconds to refresh document status.
- The frontend stores the chat session ID in browser local storage so history is preserved across reloads.
- The assistant only answers from uploaded documents and should fall back to "I do not have information about that" when the answer is not in the knowledge base.
- File uploads are limited to PDF and DOCX, up to 50MB.
- If you change the frontend port, update `APP_CORS_ALLOWED_ORIGIN` in the backend.

## 15. If You Are Looking For Hugging Face

The current implementation does not use Hugging Face.

If you expected a Hugging Face integration, that would require changing the backend services that currently call Ollama:

- `EmbeddingService`
- `LlmService`

Those are the places where the AI provider is wired in today.

## 16. Quick Start Summary

```powershell
# 1. PostgreSQL
# create database college_chatbot

# 2. ChromaDB
docker run --rm -p 8000:8000 chromadb/chroma

# 3. Ollama models
ollama pull gemma3:4b
ollama pull nomic-embed-text

# 4. Backend from demo/
$env:SPRING_DATASOURCE_PASSWORD="your_password"
.\mvnw.cmd spring-boot:run

# 5. Frontend from demo/frontend/
copy .env.example .env
npm install
npm run dev
```

Open the frontend URL printed by Vite, usually `http://localhost:5173/`.
