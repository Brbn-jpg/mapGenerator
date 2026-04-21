# MapGenerator

A simple procedural map generator using Spring Boot (Java) for the backend and Vue 3 (TypeScript) for the frontend. The project uses FastNoiseLite to generate terrain based on a seed and size.

## Project Structure

- `v1/`: Spring Boot backend application.
  - Procedural generation using OpenSimplex2 noise.
  - PostgreSQL database for storing generated maps.
  - REST API for generating and retrieving maps.
- `frontend/`: Vue 3 + TypeScript + Vite frontend.
  - Interactive UI to set parameters (size, seed).
  - **Interactive Viewport**: Google Maps style Pan and Zoom functionality.
  - **High-performance Rendering**: Uses a hybrid approach with a virtual canvas for static land and a main canvas for real-time water animations.
  - **Stylized Visuals**: Custom "Don't Starve" style water effect with procedural wave patterns and transparency.
  - **Adaptive Scaling**: Animation details and wave density automatically scale with the map resolution.

## Visual Features

### 1. Interactive Viewport
The map is displayed within a fixed 800x600 viewport, allowing for smooth navigation regardless of map size:
- **Pan**: Click and drag the map to explore different regions.
- **Zoom**: Use the mouse wheel to zoom in for detail or out for a strategic overview (centered on the cursor).

### 2. Stylized Water Animation
The map features a unique, hand-drawn aesthetic for water inspired by games like *Don't Starve*.
- **Procedural Waves**: Multiple overlapping sine waves create an organic, non-repeating movement.
- **Dynamic Transparency**: Water biomes (Abyss, Ocean, Shallow Water) have varying opacity levels, allowing the underlying wave patterns to show through.

### 3. Performance Optimizations
To handle large map sizes smoothly:
- **Virtual Canvas**: All static terrain is pre-rendered to an off-screen `landCanvas`.
- **Hybrid Drawing**: In each frame, only the water waves are procedurally calculated. The static land is then drawn on top using a single `drawImage` call.

## Biomes & Generation
The map is procedurally generated using multiple noise layers. Recent updates have **increased the frequency of mountain ranges** and high-altitude terrain for more dramatic landscapes.


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

The map is procedurally generated using multiple noise layers (height, moisture, temperature, and continent mask). The frontend maps tile IDs to specific colors to visualize different biomes:

| ID | Biome | Hex Color | Description |
|---|---|---|---|
| **Water Zone** | | | **Height < 0.40** |
| 0 | Abyss | `#000033` | Very deep ocean areas |
| 1 | Ocean | `#000080` | Standard ocean depth |
| 2 | Shallow Water | `#1E90FF` | Coastal shelf areas |
| 3 | Frozen Ocean | `#A5F2F3` | Arctic waters with ice floes |
| **Coast Zone** | | | **Height 0.40 - 0.45** |
| 4 | Bright Sand | `#FFF8DC` | Dry, hot sandy beaches |
| 5 | Sandy Beach | `#F4A460` | Standard coastal beaches |
| 6 | Rocky Beach | `#8B7D6B` | Cold, gravelly/stony coasts |
| 7 | Mangroves | `#556B2F` | Hot and very wet tropical coasts |
| **Mainland (Cold)** | | | **Height 0.45 - 0.85, Temp < 0.35** |
| 8 | Snow Desert | `#F0F8FF` | Ice sheets and frozen wastes |
| 9 | Tundra | `#BDB76B` | Permafrost and mossy plains |
| 10 | Taiga | `#2F4F4F` | Dark coniferous forests |
| **Mainland (Temperate)** | | | **Height 0.45 - 0.85, Temp 0.35 - 0.65** |
| 11 | Steppe | `#C2B280` | Dry grasslands and scrub |
| 12 | Green Plains | `#7CFC00` | Standard temperate grasslands |
| 13 | Mixed Forest | `#228B22` | Deciduous and mixed woodland |
| 14 | Swamps | `#2E8B57` | High humidity wetlands |
| **Mainland (Hot)** | | | **Height 0.45 - 0.85, Temp > 0.65** |
| 15 | Sandy Desert | `#EDC9AF` | Arid dunes and Sahara-like regions |
| 16 | Savanna | `#E6DAA6` | Tropical grasslands with sparse trees |
| 17 | Dry Shrubs | `#8FBC8F` | Chaparral and scrubland |
| 18 | Tropical Jungle | `#004B49` | Dense, humid rainforests |
| **Mountains & Peaks** | | | **Height > 0.85** |
| 19 | Canyons | `#8B4513` | Sun-scorched, arid rocky peaks |
| 20 | Gray Rocks | `#696969` | Standard high-altitude bare stone |
| 21 | Alpine Tundra | `#A9A9A9` | Cold, harsh high-altitude vegetation |
| 22 | Snowy Peak | `#FFFFFF` | Eternal snow and glaciers |
| **Special** | | | |
| 23 | City | `#FF1493` | Procedurally placed urban areas (DeepPink) |

## Technologies Used

- **Backend**: Spring Boot, Spring Data JPA, Hibernate, PostgreSQL, FastNoiseLite.
- **Frontend**: Vue 3 (Composition API), TypeScript, Vite, Axios, HTML5 Canvas.
