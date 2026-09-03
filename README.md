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

| Phase | Week | Status |
|---|---|---|
| Foundation | Week 1 | 🔄 In Progress |
| Authentication | Week 2 | ⏳ Pending |
| Vehicle + SOS | Week 3 | ⏳ Pending |
| GPS + Location | Week 4 | ⏳ Pending |
| Live Map | Week 5 | ⏳ Pending |
| Notifications + Security | Week 6 | ⏳ Pending |
| ML Anomaly Detection | Week 7 | ⏳ Pending |
| DevSecOps | Week 8 | ⏳ Pending |
| Integration Testing | Week 9 | ⏳ Pending |
| Finalization | Week 10 | ⏳ Pending |

---

## Important Notice

MedPriority is an emergency **awareness and visibility** system.
It is NOT a replacement for emergency services such as ambulances or police.
Always contact official emergency services in a medical emergency.
