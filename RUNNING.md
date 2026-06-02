# Running the College FAQ Chatbot

This project has two separate apps:

- Spring Boot backend: `http://localhost:8080`
- Vite React frontend: `http://localhost:5173`

## 1. Start required services

The backend expects PostgreSQL and ChromaDB.

### PostgreSQL
Create a database named:

```sql
CREATE DATABASE college_chatbot;
```

Default backend settings are:

- URL: `jdbc:postgresql://localhost:5432/college_chatbot`
- username: `postgres`
- password: empty

If your PostgreSQL password is not empty, set it before running Spring Boot:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="your_password"
```

### ChromaDB
Run ChromaDB on port `8000`. Example with Docker:

```powershell
docker run -p 8000:8000 chromadb/chroma
```

## 2. Start backend

From the project root folder `demo`:

```powershell
.\mvnw.cmd spring-boot:run
```

Or run `CollegeChatbotApplication.main()` from your Java IDE.

Backend API should be available at:

```text
http://localhost:8080/api
```

## 3. Start frontend

Open a second terminal in the `frontend` folder:

```powershell
npm install
npm run dev
```

Open the exact URL printed by Vite, usually:

```text
http://localhost:5173/
```

Do not open `http://localhost:8080` for the React UI. Port `8080` is only the backend API.

## Blank page checklist

1. Open `http://localhost:5173/`, not `8080`.
2. Make sure backend is running on `8080`.
3. Make sure PostgreSQL is running and the `college_chatbot` database exists.
4. Make sure ChromaDB is running on `8000`.
5. If the browser console shows CORS errors, verify `app.cors.allowed-origin` is `http://localhost:5173` in `src/main/resources/application.properties`.
6. If you changed ports, set frontend API URL in `frontend/.env`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```
