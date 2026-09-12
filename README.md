# MedPriority

**MedPriority: A Secure Real-Time Emergency Visibility and Assistance System for Private Medical Transport Vehicles**

---

## Overview

Private vehicles are frequently used to transport people during medical emergencies. Unlike ambulances, they have no standardized way to communicate urgency to surrounding road users.

MedPriority provides a **digital emergency visibility system**:

- A driver activates SOS through the MedPriority Android app
- The system creates an authenticated emergency session
- The vehicle's GPS location is shared in real-time
- Nearby participating users see the emergency vehicle on a live map
- Eligible nearby users receive a push notification

The Minor Project is **software-based**. Physical hardware is reserved for the future Major Project.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Mobile | Kotlin, Android SDK |
| Backend | Python, FastAPI |
| Database | MySQL |
| Authentication | JWT, bcrypt, RBAC |
| Notifications | Firebase Cloud Messaging (FCM) |
| Maps | Google Maps SDK for Android |
| ML | scikit-learn, Isolation Forest |
| DevSecOps | Docker, Jenkins, Kubernetes, Bandit, Trivy |

---

## Repository Structure

```
MedPriority/
├── android-app/       Kotlin Android application
├── backend/           Python FastAPI backend
├── ml/                ML anomaly detection (Isolation Forest)
├── database/          MySQL schema and migrations
├── infrastructure/    Docker Compose, NGINX, Kubernetes manifests
├── security/          Bandit config, Trivy config, security policies
├── tests/             Integration and end-to-end tests
├── docs/              Architecture, API spec, diagrams
├── .env.example       Environment variable template
└── Jenkinsfile        CI/CD pipeline definition
```

---

## Quick Start

### Prerequisites

- Python 3.11+
- Docker + Docker Compose
- Android Studio (Apple Silicon build for M1 Mac)
- MySQL 8.0 (via Docker)

### Backend

```bash
cd backend
cp ../.env.example .env
# Edit .env with your credentials

python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

docker compose -f ../infrastructure/docker-compose.yml up -d
uvicorn app.main:app --reload
```

Backend runs at: `http://localhost:8000`
API docs at: `http://localhost:8000/docs`

### Android

Open `android-app/` in Android Studio and run on an ARM64 emulator.

Configure the base URL in `android-app/local.properties`:
```
BASE_URL=http://10.0.2.2:8000/
```

---

## Development Branches

| Branch | Purpose |
|---|---|
| `main` | Stable, production-ready code only |
| `dev` | Integration branch — merge features here first |
| `backend-dev` | FastAPI backend development |
| `android-dev` | Kotlin Android development |

**Never commit directly to `main`.** All changes go through Pull Requests.

---

## Security

- Passwords hashed with bcrypt
- JWT-based authentication with expiry
- Role-based access control (USER, ADMIN)
- All secrets managed via environment variables
- Never commit `.env` files or credentials

---

## Project Status

| Phase | Week | Task / Milestone | Status |
|---|---|---|---|
| Foundation | **Week 1** | Project Setup & Git Foundation | 🟢 DONE |
| | | FastAPI backend skeleton + health check | 🟢 DONE |
| | | MySQL + SQLAlchemy ORM models | 🟢 DONE |
| | | Authentication (JWT, bcrypt, RBAC) | 🟢 DONE |
| | | Android skeleton + Navigation | 🟡 Next |
| Authentication | Week 2 | | ⏳ Pending |
| Vehicle + SOS | Week 3 | | ⏳ Pending |
| GPS + Location | Week 4 | | ⏳ Pending |
| Live Map | Week 5 | | ⏳ Pending |
| Notifications + Security | Week 6 | | ⏳ Pending |
| ML Anomaly Detection | Week 7 | | ⏳ Pending |
| DevSecOps | Week 8 | | ⏳ Pending |
| Integration Testing | Week 9 | | ⏳ Pending |
| Finalization | Week 10 | | ⏳ Pending |

---

## Architecture: Authentication (Day 4)

MedPriority uses stateless **JWT (JSON Web Token)** authentication.

### How it works:
1. Client sends `POST /api/v1/auth/login` with `username` (email) and `password`.
2. Backend verifies the password against the bcrypt hash in MySQL.
3. Backend generates a JWT containing the user ID (`sub`) and returns it.
4. Client attaches `Authorization: Bearer <token>` to future requests.
5. Protected endpoints (like `/api/v1/auth/me`) decode the token to identify the user.

### RBAC (Role-Based Access Control)
Users have roles (`USER` or `ADMIN`). You can restrict endpoints using FastAPI dependencies:
- `Depends(get_current_active_user)` — Any logged-in user.
- `Depends(get_current_admin_user)` — Admin only (e.g. `/api/v1/auth/admin-test`).

> **Security Rule:** The database stores `password_hash`. The plain password is never saved. Furthermore, the `password_hash` is **never** included in API responses.

---

## Architecture: Vehicle Management (Day 5)

Vehicles are explicitly linked to authenticated users.
- A user can register multiple vehicles (One-to-Many).
- Vehicle ownership checks are strictly enforced server-side.
- `user_id` is always derived from the authenticated JWT (Server-side identity binding) — users cannot spoof vehicle creation for other users.
- Unique `vehicle_number` validation.

---

## Architecture: Emergency SOS Sessions (Day 6)

The core mechanism representing an active emergency.
An Emergency Session binds a **User** and a specific **Vehicle** they own into an active distress state.

### Lifecycle
- **ACTIVE**: The SOS is currently live.
- **ENDED**: The user manually turned off the SOS.
- **EXPIRED**: The system automatically closed the SOS because the 2-hour timeout was reached.

### Security & Rules
- Users can only start an SOS for a vehicle they own.
- Users cannot have multiple overlapping ACTIVE emergencies.
- Endpoints enforce RBAC (Users manage their own emergencies, Admins can view all history).
- Session expiration is checked dynamically (on-query) to guarantee expired sessions act identically to ended sessions without relying on a background worker.
- The schema forbids users from sending timestamps or setting the `status` field explicitly — these are strictly server-controlled.

---

## Important Notice

MedPriority is an emergency **awareness and visibility** system.
It is NOT a replacement for emergency services such as ambulances or police.
Always contact official emergency services in a medical emergency.

---

## Architecture: Location Tracking (Day 7)

Location tracking links the active `EmergencySession` with real-time GPS coordinates provided by the Android client.

### Android Client
- **Architecture**: Modern Android architecture using **Kotlin**, **Jetpack Compose**, and **Coroutines**.
- **Networking**: `Retrofit2` with an `OkHttp` interceptor that automatically attaches the JWT to every request.
- **Security**: The JWT is securely stored in Android's `EncryptedSharedPreferences` (AES256_GCM).
- **Location**: Uses `FusedLocationProviderClient` to request high-accuracy foreground location updates every 5-10 seconds.
- **Privacy**: The app explicitly requests foreground `ACCESS_FINE_LOCATION`. If denied, it does not crash but falls back to a standby state.

### Backend Endpoint
`POST /api/v1/emergencies/{id}/location`
- Receives GPS coordinates.
- Strictly validates that the JWT `user_id` matches the owner of the `EmergencySession`.
- Validates the session is exactly in the `ACTIVE` state.
- Records the data in the `location_updates` MySQL table securely.

### Data Flow Diagram
```text
Android App 
    |  Requests Foreground Location
    v
GPS Hardware
    |  Returns (Lat, Lng)
    v
Retrofit Client (Attaches JWT)
    |  POST /api/v1/emergencies/{id}/location
    v
FastAPI Backend
    |  Verifies JWT, Ownership, & Session == ACTIVE
    v
MySQL Database (location_updates)
```
