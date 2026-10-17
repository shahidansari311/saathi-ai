<p align="center">
  <img src="assets/saathi_logo.png" alt="Saathi AI Logo" width="180px">
</p>

<h1 align="center">Saathi AI (साथी AI)</h1>

<p align="center">
  <strong>🌉 Bridging the Generational & Accessibility Divide Through Autonomous Voice-First Mobile AI</strong>
</p>

<p align="center">
  <em>Empowering elderly and non-tech-savvy individuals to navigate smartphones effortlessly using natural voice commands and autonomous on-screen actions.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Devlympics-Bridge%20The%20Gap-0052FF?style=for-the-badge&logo=google&logoColor=white" alt="Devlympics">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-blue?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-purple?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green?style=for-the-badge" alt="License">
</p>

---

## 🎯 The "Bridge the Gap" Problem Statement

Modern smartphones are powerful, but for **millions of elderly citizens, individuals with visual or motor impairments, and non-tech-savvy users**, daily digital tasks are an intimidating maze of menus, icons, and multi-step app flows.

| The Divide | The Challenge | How Saathi AI Bridges It |
|---|---|---|
| **Digital Literacy Gap** | Sending a WhatsApp message requires 6+ taps, searching contacts, and typing on a small keyboard. | Speak naturally: *"Beti ko WhatsApp bhejo ki main theek hoon"* $\to$ Saathi executes it autonomously. |
| **Language & Dialect Barrier** | Most phone interfaces default to English UI terminology. | Understood via natural Hinglish & Indian vernacular voice processing. |
| **Emergency & Care Barrier** | Panic situations make unlocking and navigating phones nearly impossible. | One-word voice panic trigger (*"Bachao"* / *"Emergency"*) broadcasts location, battery & distress alerts. |
| **Cognitive Memory Load** | Remembering app layouts, settings toggles, and daily pill routines. | In-app 4-tier Knowledge Graph remembers daily routines and family relationships. |

---

## 🏗️ Technical Architecture & End-to-End Flow

Saathi AI operates as an **Autonomous On-Device Agent** bridging user voice intent directly to Android operating system actions:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        1. USER VOICE / SPEECH                          │
│               "Torch jala do aur Beta ko phone lagao"                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               2. ZERO-LATENCY ALIAS RESOLVER (On-Device)               │
│         Checks local vocabulary cache for instant 0ms triggers         │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ [Bypass if direct alias]       │ [Complex intent]
                    │                                ▼
                    │               ┌────────────────────────────────────┐
                    │               │    3. INTENT CLASSIFIER & AGENT    │
                    │               │       Context: Battery, Time,      │
                    │               │        Foreground App, Memory      │
                    │               └────────────────┬───────────────────┘
                    │                                │
                    │                                ▼
                    │               ┌────────────────────────────────────┐
                    │               │     4. MULTI-LLM REASONING         │
                    │               │  (Gemini 2.0 / Groq / Local Model) │
                    │               │    Outputs Structured JSON Plan    │
                    │               └────────────────┬───────────────────┘
                    │                                │
                    │                                ▼
                    │               ┌────────────────────────────────────┐
                    │               │   5. SAFETY CRITIC & VALIDATOR     │
                    │               │     Evaluates Risk & Guardrails    │
                    │               └────────────────┬───────────────────┘
                    │                                │
                    ▼                                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      6. ACTION DISPATCHER & CORE                       │
├───────────────────────────────────┬────────────────────────────────────┤
│     NATIVE SYSTEM ACTIONS         │   ACCESSIBILITY SERVICE ENGINE     │
│  - Calls & SMS                    │  - Android Accessibility Node Tree │
│  - Flashlight, WiFi, Volume       │  - Traverses UI Hierarchy          │
│  - Medicine Alarms & Reminders    │  - Simulates Touch, Click & Typing │
│  - Live GPS SOS Emergency Broadcast│ - Autonomous WhatsApp Navigation  │
└───────────────────────────────────┴────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      7. RE-EVALUATION & VISION                         │
│    Inspects screen state post-action; auto-repairs plan if app fails   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📱 The 4 Core Accessible Modules

1. **🎙️ Saathi Assistant (Voice & Chat)**:
   - Voice-first conversational companion.
   - Speak in natural English or Hinglish without memorizing commands.
   - Text-to-Speech audio feedback confirms each executed step aloud.

2. **⚡ Action Engine & Plan Viewer**:
   - Transparent step-by-step display of what the assistant is performing.
   - Full pause, cancel, and review controls for total user safety.

3. **🧠 Care Memory (4-Tier Knowledge Graph)**:
   - **Tier 1 (Working)**: Current conversational goal.
   - **Tier 2 (Episodic)**: Prior instructions and routine history.
   - **Tier 3 (Semantic)**: Known family aliases (*"Beta" = Rahul*, *"Doctor" = Dr. Sharma*).
   - **Tier 4 (Hardware-Encrypted)**: Android Keystore AES-GCM protected credentials.

4. **⚙️ Simplified Caregiver Settings**:
   - Quick setup of Emergency SOS family contacts.
   - One-tap selection of AI provider (Google Gemini, Groq, Claude, OpenAI, or local Ollama).

---

## 🛠️ Tech Stack & Implementation Details

- **Language**: Kotlin 2.0 (JVM 21)
- **UI Framework**: Android Jetpack Compose with custom glassmorphic theme
- **Dependency Injection**: Dagger-Hilt
- **Local Persistence**: Android Room Database (SQLCipher / Android Keystore encrypted)
- **Automation Pipeline**: Android Accessibility Framework (`AccessibilityNodeInfo`, `performAction(ACTION_CLICK)`)
- **Speech Stack**: Android `SpeechRecognizer` + Android Native TTS / ElevenLabs
- **LLM Engine**: Multi-provider client with automatic smart-fallback (Gemini Flash, Groq LLaMA-3, Claude, OpenAI, Ollama)

---

## 🚀 Quick Start Guide

### Prerequisites
- JDK 21
- Android Studio Ladybug / Meerkat or Android SDK 35+

### Build & Run
```bash
# Clone the repository
git clone https://github.com/yashab-cyber/opendroid.git
cd saathi-ai

# Compile and build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### Direct Download
Grab the latest pre-compiled APK directly from the [GitHub Releases](https://github.com/yashab-cyber/opendroid/releases) page.

---

## 👥 Hackathon Team & Project Role Division

- **AI Agent, Voice Intent & Actions Engine**: Backend reasoning, Hinglish/Hindi prompt engineering, Emergency SOS handler, and Accessibility Automation dispatcher.
- **Frontend, Voice UI & Accessibility Experience**: Jetpack Compose senior-friendly UI, high-contrast dashboard, voice feedback visualization, and settings integration.

---

## 📄 License

Licensed under the **Apache License 2.0**.
