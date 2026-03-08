# Lytic 🚀

An advanced advertisement analysis platform that leverages biometric engagement metrics and AI-driven insights to help businesses optimize their video campaigns.

## 🛠 Technology Stack

### Frontend
- **React 19**: Powered by the latest React features for a responsive and high-performance UI.
- **Vite 7**: Ultra-fast build tool and development server.
- **Vanilla CSS & Glassmorphism**: A custom-built, premium design system utilizing HSL colors, semi-transparent overlays, and fluid animations for a state-of-the-art user experience.
- **Google Fonts (Outfit)**: Modern typography for enhanced readability and aesthetic appeal.

### Backend
- **FastAPI**: A high-performance, modern web framework for building APIs with Python 3.8+.
- **Google GenAI SDK**: Deep integration with Gemini models for advanced video understanding and content analysis.
- **MoviePy**: Robust video processing library used for handling media files and metadata.
- **Pydantic**: Data validation and settings management using Python type annotations.
- **Uvicorn**: Lightning-fast ASGI server implementation.

### AI & Analytics
- **Gemini 2.5 Flash**: Leveraging the power of Google's multimodal models to analyze video scenes, transcripts, and engagement patterns.
- **Biometric Integration**: Real-time analysis of pulse rates, breathing, and engagement scores.

---

## 🚀 Getting Started

### Frontend Setup
```bash
cd Frontend/antigravity-frontend
npm install
npm run dev
```

### Backend Setup
```bash
cd Backend
pip install -r requirements.txt
python -m uvicorn backend_1:app --reload
```

## 📊 Core Features
- **Video Upload & Management**: Seamlessly upload and manage .mp4 advertisement files.
- **AI Analysis Dashboard**: Dedicated view for in-depth analysis including overall match scores, specific points of interest (Hook, Clarity, Pacing, CTA), and strategic insights.
- **Timeline Analysis**: Visual breakdown of strong and weak moments within the video timeline.
- **Actionable Recommendations**: AI-generated suggestions to improve viewer retention and conversion.