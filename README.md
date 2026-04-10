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
  - **Returns**: `Long` ID of the generated map.
- `GET /generate/{id}`: Retrieves map metadata and status.
  - **Returns**: `GeneratedMap` object.
- `GET /generate/{id}/chunks`: Retrieves chunks for a map.
  - **Query Param**: `afterId` (optional) to fetch only new chunks.
  - **Returns**: `List<MapChunk>` containing chunk coordinates and tile data.

## Biomes & Legend

The map is divided into chunks, each containing a flattened 1D array of integers representing tile IDs. The frontend maps these IDs to specific colors based on height and moisture:

| ID | Biome | Hex Color | Range (Height) |
|---|---|---|---|
| **Water Level** | | | **< 0.3** |
| 0 | Void | `#000044` | |
| 4 | Deep ocean | `#00008B` | |
| 9 | Narrow water (near coast) | `#1E90FF` | |
| **Low Level** | | | **0.3 - 0.5** |
| 10 | Bright sand (dry coast) | `#F5F5DC` | |
| 5 | Sandy beach (standard) | `#F4A460` | |
| 1 | Swamps (high moisture) | `#2E8B57` | |
| 11 | Mangroves (high moisture) | `#556B2F` | |
| **Medium Level** | | | **0.5 - 0.7** |
| 8 | Desert (low moisture) | `#D2B48C` | |
| 12 | Savanna (mid moisture) | `#C2B280` | |
| 6 | Green grass (high moisture) | `#32CD32` | |
| 13 | Forest (very high moisture) | `#008000` | |
| **High Level** | | | **0.7 - 0.85** |
| 14 | Taiga (high moisture) | `#4B5320` | |
| 2 | Dark forest / Jungle | `#006400` | |
| 3 | Dry mountains / canyons | `#8B4513` | |
| **Very High Level** | | | **> 0.85** |
| 15 | Rock (tall mountains) | `#808080` | |
| 7 | Snowy peaks | `#FFFFFF` | |

## Technologies Used

- **Backend**: Spring Boot, Spring Data JPA, Hibernate, PostgreSQL, FastNoiseLite.
- **Frontend**: Vue 3 (Composition API), TypeScript, Vite, Axios, HTML5 Canvas.
