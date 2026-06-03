# How to Run the College FAQ Chatbot Project

This project has three connected parts:

1. **Frontend**: React + Vite app in `frontend/`, runs on `http://localhost:5173`
2. **Backend**: Spring Boot app in the parent `demo/` folder, runs on `http://localhost:8080`
3. **Pipeline / Knowledge Base**: PostgreSQL + ChromaDB + Ollama

The frontend talks to the backend through:

```text
VITE_API_BASE_URL=http://localhost:8080/api
```

The backend talks to:

- PostgreSQL for chat sessions, uploaded document metadata, chunks, and feedback
- ChromaDB for vector search
- Ollama for embeddings and answers using `gemma3:4b` and `nomic-embed-text`

---x

## Prerequisites

Install these first:

- Java 17
- Node.js 18 or newer
- npm
- PostgreSQL
- Docker Desktop, recommended for ChromaDB
- Ollama installed locally
- The `gemma3:4b` and `nomic-embed-text` models pulled into Ollama

---

## 1. Start PostgreSQL

Create the database used by the backend:

```sql
CREATE DATABASE college_chatbot;
```

Default backend database settings are in `../src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/college_chatbot
spring.datasource.username=postgres
spring.datasource.password=
```

If your PostgreSQL password is not empty, set it before running the backend:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="your_postgres_password"
```

You can also override the full URL and username:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/college_chatbot"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="your_postgres_password"
```

---

## 2. Start ChromaDB

Run ChromaDB on port `8000`:

```powershell
docker run --rm -p 8000:8000 chromadb/chroma
```

Backend default ChromaDB settings:

```properties
chromadb.base.url=http://localhost:8000
chromadb.collection.name=college_docs
chromadb.tenant=default_tenant
chromadb.database=default_database
```

If you use a different ChromaDB URL:

```powershell
$env:CHROMADB_BASE_URL="http://localhost:8000"
```

---

## 3. Start Ollama and pull models

The document pipeline and chat answers use Ollama locally:

```powershell
ollama pull gemma3:4b
ollama pull nomic-embed-text
```

Make sure Ollama is available at:

```text
http://localhost:11434
```

Without Ollama or the models:

- File upload may fail during embedding
- Chat questions may return an error from the backend

---

## 4. Start the Spring Boot backend

Open a terminal in the parent project folder, not in `frontend`:

```powershell
cd ..
.\mvnw.cmd spring-boot:run
```

The backend should start at:

```text
http://localhost:8080
```

The API base URL is:

```text
http://localhost:8080/api
```

Important backend endpoints used by the frontend:

```text
POST   /api/chat
GET    /api/chat/history/{sessionId}
POST   /api/chat/feedback
GET    /api/admin/documents
POST   /api/admin/upload
DELETE /api/admin/documents/{id}
```

---

## 5. Configure frontend backend URL

In the `frontend` folder, create `.env` from the example:

```powershell
copy .env.example .env
```

The value should be:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

If you change the backend port, update this value.

---

## 6. Start the React frontend

Open another terminal in `frontend`:

```powershell
npm install
npm run dev
```

Open the URL printed by Vite, usually:

```text
http://localhost:5173/
```

Do **not** open `http://localhost:8080` for the React UI. Port `8080` is only the backend API.

---

## 7. How the full pipeline works

```text
React frontend
  -> Spring Boot REST API
  -> PostgreSQL stores document records, chat messages, feedback
  -> Uploaded PDF/DOCX is read and chunked
  -> Ollama creates embeddings for chunks
  -> ChromaDB stores vectors
  -> User asks a question
  -> Ollama embeds the question
  -> ChromaDB returns similar chunks
  -> Ollama generates the final answer
  -> React displays answer and sources
```

---

## 8. Validation commands already checked

From `frontend`:

```powershell
npm install
npm run build
npm run lint
```

From the parent backend folder:

```powershell
.\mvnw.cmd test
```

Both frontend and backend validations passed.

---

## Troubleshooting

### Frontend shows backend offline

Check that backend is running:

```text
http://localhost:8080/api/admin/documents
```

If the backend is on another port, update `frontend/.env`:

```env
VITE_API_BASE_URL=http://localhost:YOUR_PORT/api
```

Restart `npm run dev` after changing `.env`.

### CORS error in browser console

The backend allows this frontend origin by default:

```properties
app.cors.allowed-origin=http://localhost:5173
```

If your frontend runs on another port:

```powershell
$env:APP_CORS_ALLOWED_ORIGIN="http://localhost:5174"
```

Then restart the backend.

### Backend fails to start because PostgreSQL connection fails

Make sure:

1. PostgreSQL is running
2. Database `college_chatbot` exists
3. `SPRING_DATASOURCE_PASSWORD` is correct

### Upload or chat fails because Ollama is unavailable

Make sure Ollama is running and the models are pulled:

```powershell
ollama pull gemma3:4b
ollama pull nomic-embed-text
```

Restart the backend after Ollama is available.

### Upload indexing fails because ChromaDB is offline

Start ChromaDB:

```powershell
docker run --rm -p 8000:8000 chromadb/chroma
```

Then upload the document again.

---

## Quick start summary

Terminal 1, ChromaDB:

```powershell
docker run --rm -p 8000:8000 chromadb/chroma
```

Terminal 2, Ollama:

```powershell
ollama pull gemma3:4b
ollama pull nomic-embed-text
```

Terminal 3, backend from parent `demo` folder:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="your_postgres_password"
.\mvnw.cmd spring-boot:run
```

Terminal 4, frontend from `frontend` folder:

```powershell
copy .env.example .env
npm install
npm run dev
```

Open:

```text
http://localhost:5173/
```
