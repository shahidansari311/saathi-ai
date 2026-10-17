<p align="center">
  <img src="assets/saathi_logo.png" alt="Saathi AI Logo" width="180px">
</p>

<h1 align="center">Saathi AI (साथी AI)</h1>

<p align="center">
  <strong>🤖 Autonomous Voice-First Mobile AI Companion for Android</strong>
</p>

<p align="center">
  <em>Your Personal Mobile Companion. Empowering Everyone to Navigate and Automate Their Phone Effortlessly.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2014%2B-blue?style=for-the-badge&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License">
</p>

<p align="center">
  <a href="#-what-is-saathi-ai">About</a> •
  <a href="#-features">Features</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#%EF%B8%8F-getting-started">Get Started</a> •
  <a href="#-supported-llm-providers">Providers</a> •
  <a href="#-license">License</a>
</p>

---

## 🎯 What is Saathi AI?

**Saathi AI** (*साथी* meaning companion/friend) is an autonomous, voice-driven AI companion designed to break digital barriers and assist individuals who struggle with smartphone navigation. Whether it's the elderly, visually impaired, or everyday users who need hands-free assistance, Saathi AI turns spoken intent into autonomous on-screen actions.

Instead of navigating complex menus and multi-step app flows, simply speak:

> *"Saathi, send a WhatsApp message to my daughter asking if she reached safely, and set an alarm for my 6 PM medicine."*

Saathi AI **understands the request**, constructs a sequential **action plan**, interacts with the phone's native services and applications via **Accessibility Automation**, and verifies completion—all autonomously.

---

## ✨ Key Features

### 🎙️ Voice-First Interaction
- **Hands-Free Operation**: Speak naturally without needing to remember exact command syntax.
- **Speech-to-Text & Text-to-Speech**: Conversational voice feedback ensuring the user always knows what action is happening.
- **Offline & Low-Bandwidth Resilience**: Fallback to local and on-device models.

### 🧠 Autonomous Action Engine
- **Sequential Task Planning**: Deconstructs multi-step goals with dependency tracking and safe validation.
- **Safety & Privacy Critic**: Evaluates action risk levels before executing sensitive operations (e.g. transfers, deletions).
- **Silent Self-Correction**: If an app UI shifts or fails, the Re-Evaluation engine inspects the screen and dynamically adapts.

### 📱 Full Device & App Control
| Domain | Capabilities |
|--------|--------------|
| **Communication** | WhatsApp messaging, SMS, phone calls, contact lookup & alias resolution ("call Beta") |
| **System Settings** | WiFi, Bluetooth, Flashlight, Volume, Brightness, DND toggles |
| **Productivity & Health** | Medicine alarms, reminders, calendar events, timer setup |
| **Accessibility & Assistance** | Screen reading, button clicking, automated app navigation |

### 🗄️ 4-Tier Memory & Knowledge Graph
- **Working Memory**: Current task context.
- **Episodic Memory**: Conversation history.
- **Semantic Memory**: User facts, family relationships ("Beta", "Doctor"), and app preferences.
- **Hardware-Encrypted Storage**: Sensitive credentials and tokens stored in Android Keystore with AES-GCM encryption.

---

## 🏗️ Architecture

```
com.opendroid.ai (Saathi AI Core)
│
├── 🤖 accessibility/      Accessibility Service (UI tree traversal, taps, automated text entry)
├── ⚡ actions/             60+ action handlers (Communication, System, Media, Calendar, Reminders)
├── 🧠 core/
│   ├── agent/              AgentLoop, PlanManager, ReEvaluationEngine, Safety Critic
│   ├── llm/                12 LLM providers, prompt engine, smart fallback chain
│   ├── memory/             4-tier Room database + Keystore encryption
│   └── voice/              Wake word, STT, and TTS engines
│
├── 💾 data/                Room DB entities, DAOs, and repository layer
├── 💉 di/                  Dagger-Hilt dependency injection modules
└── 🎨 ui/
    ├── theme/              Glassmorphic modern UI theme
    ├── screens/            Chat, Plan Viewer, Memory Manager, Settings, About
    └── components/         Compose UI components
```

---

## 🔌 Supported LLM Providers

Saathi AI supports cloud and on-device models with automatic failover:

| Provider | Type | Recommended For |
|----------|------|-----------------|
| 🟢 **Google Gemini** | Cloud / On-Device | High accuracy, multi-modal vision |
| ⚡ **Groq** | Cloud | Ultra-low latency voice responses |
| 🔵 **OpenAI (GPT-4o)** | Cloud | Complex multi-step reasoning |
| 🟣 **Anthropic Claude** | Cloud | Careful safety adherence |
| 🏠 **Ollama / LiteRT** | On-Device / Local | Complete offline privacy |

---

## ⚡️ Getting Started

### Prerequisites
- **JDK 21**
- **Android SDK 35+** (Android 14 / 15)
- Android Studio Ladybug / Meerkat or newer

### Build & Run
```bash
# Clone the repository
git clone <your-repo-url>
cd opendroid

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### Essential Permissions
To allow Saathi AI to perform actions on your behalf, enable the following on first setup:
1. **Accessibility Service**: Allows Saathi AI to navigate and click buttons in apps like WhatsApp.
2. **Microphone**: For voice listening and speech recognition.
3. **Contacts & Phone**: For dialing and contact identification.

---

## 🔒 Security & Privacy

Saathi AI adheres to strict on-device privacy principles:
- Sensitive credentials are encrypted using **Android Keystore (AES-GCM)**.
- Destructive actions (data wipes, contact deletions) are blocked by the built-in **Safety Critic**.
- Voice audio is processed transiently and not stored on remote servers without permission.

---

## 📄 License

Licensed under the **Apache License 2.0**.
