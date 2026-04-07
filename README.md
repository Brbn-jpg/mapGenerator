# MapGenerator

A simple procedural map generator using Spring Boot (Java) for the backend and Vue 3 (TypeScript) for the frontend. The project uses FastNoiseLite to generate terrain based on a seed and size.

## Project Structure

- `v1/`: Spring Boot backend application.
  - Procedural generation using OpenSimplex2 noise.
  - PostgreSQL database for storing generated maps.
  - REST API for generating and retrieving maps.
- `frontend/`: Vue 3 + TypeScript + Vite frontend.
  - Interactive UI to set parameters (size, seed).
  - High-performance `<canvas>` rendering for map visualization.

## Prerequisites

- **Java 17+**
- **Node.js 18+** & **npm**
- **Docker** (for PostgreSQL)

## Getting Started

### 1. Database Setup
The backend is configured to connect to PostgreSQL on port `5455`. You can use the provided `docker-compose.yml` in the `v1/` directory:
```bash
cd v1
docker-compose up -d
```

### 2. Environment Variables
Create a `.env` file in the root directory with the following content:
```env
export DB_URL="jdbc:postgresql://localhost:5455/mapGenerator_db"
export POSTGRES_USER="postgres"
export POSTGRES_PASSWORD="postgres"
export POSTGRES_DB="mapGenerator_db"
```

### 3. Backend (Spring Boot)
Run the backend application using Maven:
```bash
cd v1
./mvnw spring-boot:run
```
The API will be available at `http://localhost:8080`.

### 3. Frontend (Vue)
Install dependencies and start the development server:
```bash
cd frontend
npm install
npm run dev
```
The UI will be available at `http://localhost:5173`.

## API Endpoints

- `POST /generate`: Creates a new map.
  - **Body**: `{ "size": 100, "seed": 12345 }`
  - **Returns**: `UUID` of the generated map.
- `GET /generate/{id}`: Retrieves map data.
  - **Returns**: `GeneratedMap` object containing the `int[]` array.

## Biomes & Legend

Maps are generated as a flattened 1D array of integers. The frontend maps these IDs to colors:

| ID | Biome | Color | Range (Height) |
|---|---|---|---|
| 0 | Ocean | Blue | < 0.3 |
| 1 | Beach | Sand | 0.3 - 0.5 |
| 2 | Grass | Green | 0.5 - 0.8 |
| 3 | Mountains | Gray | 0.8 - 0.9 |
| 4 | Snowy Peaks | White | > 0.9 |

## Technologies Used

- **Backend**: Spring Boot, Spring Data JPA, Hibernate, PostgreSQL, FastNoiseLite.
- **Frontend**: Vue 3 (Composition API), TypeScript, Vite, Axios, HTML5 Canvas.
