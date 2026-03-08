import React, { useRef, useState } from 'react';
import './index.css';

const parseAnalysis = (result) => {
  if (!result) return null;
  console.log("Raw analysis result to parse:", result);
  
  let parsed = result;

  if (typeof parsed === 'string') {
    try {
      let cleanStr = parsed.replace(/```json/gi, '').replace(/```/g, '').trim();
      const firstBrace = cleanStr.indexOf('{');
      const lastBrace = cleanStr.lastIndexOf('}');
      if (firstBrace !== -1 && lastBrace !== -1 && lastBrace >= firstBrace) {
          cleanStr = cleanStr.substring(firstBrace, lastBrace + 1);
      }
      parsed = JSON.parse(cleanStr);
    } catch (e) {
      console.error("Failed to parse analysis result string:", e);
      return null;
    }
  }

  if (typeof parsed !== 'object' || parsed === null) return null;
  
  // Normalize all keys to lowercase for consistent access
  const normalized = {};
  Object.keys(parsed).forEach(key => {
    normalized[key.toLowerCase()] = parsed[key];
  });

  return normalized;
};

const AnalysisDashboard = ({ data, onBack }) => {
  // Map normalized keys (lowercase) to component variables
  const overallScore = data.overallscore || 0;
  const hookScore = data.hookscore || 0;
  const clarityScore = data.clarityscore || 0;
  const pacingScore = data.pacingscore || 0;
  const ctaScore = data.ctascore || 0;
  const strongMoments = data.strongmoments || [];
  const weakMoments = data.weakmoments || [];
  const strongPoints = data.strongpoints || [];
  const lowPoints = data.lowpoints || [];
  const suggestions = data.suggestions || [];
  const keyInsight = data.keyinsight || "";

  const radius = 88;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (overallScore / 100) * circumference;

  return (
    <div className="analysis-results-container">
      <div className="dashboard-header">
        <button className="btn-back-home" onClick={onBack}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          Back to Dashboard
        </button>
        <div className="dashboard-title-group">
          <h2 className="dashboard-main-title">Campaign Analysis</h2>
          <p className="dashboard-subtitle">AI-driven engagement metrics and insights</p>
        </div>
      </div>

      <div className="analysis-main-grid">
        {/* Left Column: Scores */}
        <div className="metrics-column">
          <div className="glass-card score-card-large">
            <h3 className="card-label">Overall Performance</h3>
            <div className="circular-progress-container">
              <svg width="220" height="220" viewBox="0 0 200 200">
                <circle cx="100" cy="100" r={radius} className="circle-bg" />
                <circle
                  cx="100"
                  cy="100"
                  r={radius}
                  className="circle-progress"
                  style={{ 
                    strokeDasharray: circumference, 
                    strokeDashoffset: isNaN(strokeDashoffset) ? circumference : strokeDashoffset,
                    transition: 'stroke-dashoffset 1.5s cubic-bezier(0.4, 0, 0.2, 1)'
                  }}
                />
              </svg>
              <div className="score-display">
                <span className="score-number">{overallScore}</span>
                <span className="score-total">/100</span>
              </div>
            </div>
            
            <div className="sub-metrics-grid">
              <div className="sub-metric-item">
                <div className="sub-metric-value">{hookScore}/10</div>
                <div className="sub-metric-name">Hook</div>
              </div>
              <div className="sub-metric-item">
                <div className="sub-metric-value">{clarityScore}/10</div>
                <div className="sub-metric-name">Clarity</div>
              </div>
              <div className="sub-metric-item">
                <div className="sub-metric-value">{pacingScore}/10</div>
                <div className="sub-metric-name">Pacing</div>
              </div>
              <div className="sub-metric-item">
                <div className="sub-metric-value">{ctaScore}/10</div>
                <div className="sub-metric-name">CTA</div>
              </div>
            </div>
          </div>

          {keyInsight && (
            <div className="glass-card insight-card">
              <div className="insight-header">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>
                <h3>Strategic Insight</h3>
              </div>
              <p>{keyInsight}</p>
            </div>
          )}
        </div>

        {/* Right Column: Details */}
        <div className="details-column">
          <div className="glass-card">
            <h3 className="card-title positive">Strong Points</h3>
            <ul className="impact-list">
              {strongPoints.map((p, i) => (
                <li key={i}><span className="check">✓</span> {p}</li>
              ))}
            </ul>
          </div>

          <div className="glass-card">
            <h3 className="card-title negative">Areas for Improvement</h3>
            <ul className="impact-list">
              {lowPoints.map((p, i) => (
                <li key={i}><span className="cross">×</span> {p}</li>
              ))}
            </ul>
          </div>

          <div className="glass-card full-width">
            <h3 className="card-title accent">Actionable Recommendations</h3>
            <div className="recommendations-grid">
              {suggestions.map((s, i) => (
                <div key={i} className="rec-item">
                  <div className="rec-number">{i + 1}</div>
                  <p>{s}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="moments-section">
        <h2 className="section-title-premium">Timeline Analysis</h2>
        <div className="moments-grid">
          <div className="moments-column-half">
            <h3 className="moment-type-title strong">Strong Moments</h3>
            {strongMoments.map((m, i) => (
              <div key={i} className="moment-mini-card strong">
                <div className="moment-time">{m.timestamp}</div>
                <div className="moment-content">
                  <h4>{m.description}</h4>
                  <p>{m.reason}</p>
                </div>
              </div>
            ))}
          </div>
          <div className="moments-column-half">
            <h3 className="moment-type-title weak">Weak Moments</h3>
            {weakMoments.map((m, i) => (
              <div key={i} className="moment-mini-card weak">
                <div className="moment-time">{m.timestamp}</div>
                <div className="moment-content">
                  <h4>{m.description}</h4>
                  <p>{m.reason}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

function App() {
  const fileInputRef = useRef(null);

  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState('');
  const [result, setResult] = useState(null);
  const [showVideos, setShowVideos] = useState(false);
  const [videos, setVideos] = useState([]);
  const [selectedVideo, setSelectedVideo] = useState(null);
  const [view, setView] = useState('home');
  const [analysisResult, setAnalysisResult] = useState(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);

  const fetchVideos = async () => {
    try {
      const response = await fetch('http://127.0.0.1:8000/videos');
      const data = await response.json();
      if (data.videos) {
        setVideos(data.videos);
      }
    } catch (error) {
      console.error('Error fetching videos:', error);
    }
  };

  const toggleVideoList = () => {
    const willShow = !showVideos;
    setShowVideos(willShow);
    if (willShow) {
      fetchVideos();
    }
  };
  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    try {
      setUploading(true);
      setMessage('Uploading...');
      const response = await fetch('http://127.0.0.1:8000/upload-ad', {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      if (!response.ok) throw new Error(data.detail || 'Upload Failed');

      setMessage('Upload successful');
      setTimeout(() => setMessage(''), 3000);
    } catch (error) {
      console.error('Upload error:', error);
      setMessage(`Upload failed: ${error.message}`);
    } finally {
      setUploading(false);
      if (event.target) event.target.value = '';
    }
  };

  return (
    <div className="app-container">
      {view === 'home' && (
      <main className="content-wrapper">
        <div className="logo-container">
          <h1 className="logo-text">
            Lytic<span className="logo-accent">.</span>
          </h1>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', alignItems: 'center' }}>
          <input
            type="file"
            accept=".mp4"
            ref={fileInputRef}
            onChange={handleFileChange}
            style={{ display: 'none' }}
          />

          <button className="btn-upload" onClick={handleUploadClick} disabled={uploading}>
            <svg
              className="upload-icon"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
            {uploading ? 'Uploading... ' : 'Upload'}
          </button>

          <button className="btn-secondary" onClick={toggleVideoList}>
            <svg
              className="upload-icon"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            {showVideos ? 'Hide Videos' : 'Select Video'}
          </button>
        </div>
        {message && <p style={{ marginTop: '20px' }}>{message}</p>}

        {showVideos && (
          <div className="video-section">
            <h3 className="section-title">Uploaded Videos</h3>
            {videos.length === 0 ? (
              <p style={{ color: 'var(--text-muted)' }}>No videos found in Uploads folder.</p>
            ) : (
              <>
                <div className="video-grid">
                  {videos.map(video => (
                    <div
                      key={video.id}
                      className={`video-card ${selectedVideo === video.id ? 'selected' : ''}`}
                      onClick={() => setSelectedVideo(video.id)}
                    >
                      <div className="video-thumbnail">
                        <video src={video.url} preload="metadata" muted playsInline disablePictureInPicture className="thumbnail-player" />
                        <svg className="play-icon" fill="currentColor" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                        </svg>
                      </div>
                      <div className="video-info">
                        <div className="video-title">{video.name}</div>
                      </div>
                    </div>
                  ))}
                </div>

                <div style={{ marginTop: '2rem', textAlign: 'center' }}>
                  <button
                    className="btn-analyze"
                    disabled={!selectedVideo}
                    onClick={async () => {
                      setView('analysis');
                      setAnalysisResult(null);
                      setAnalysisLoading(true);
                      try {
                        const ad_id = selectedVideo.split('_')[0];
                        const response = await fetch('http://127.0.0.1:8000/analysis/start', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ ad_id })
                        });
                        const data = await response.json();
                        setAnalysisResult(data);
                      } catch (error) {
                        console.error('Failed to start analysis:', error);
                      } finally {
                        setAnalysisLoading(false);
                      }
                    }}
                  >
                    <svg className="upload-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    Analyze
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </main>
      )}

      {view === 'analysis' && (
        <main className="content-wrapper analysis-view">
          <div className="analysis-video-container">
            {selectedVideo && (
              <video 
                src={videos.find(v => v.id === selectedVideo)?.url.split('#')[0]} 
                controls 
                autoPlay
                className="analysis-video-player"
              />
            )}
          </div>
          
          <div style={{ marginTop: '2rem', width: '100%', maxWidth: '1200px', margin: '0 auto', color: 'var(--text-color)' }}>
            {analysisLoading && (
              <div className="loading-container">
                <div className="shimmer-loader"></div>
                <p>Analyzing video engagement...</p>
              </div>
            )}
            {analysisResult && (() => {
              const parsedAnalysis = parseAnalysis(analysisResult);
              if (!parsedAnalysis || Object.keys(parsedAnalysis).length === 0) {
                return (
                  <div className="error-card">
                    <h3>Analysis Error</h3>
                    <p>We couldn't parse the analysis results. Please try again.</p>
                    <button className="btn-secondary" onClick={() => setView('home')}>Back to Home</button>
                  </div>
                );
              }
              return <AnalysisDashboard data={parsedAnalysis} onBack={() => setView('home')} />;
            })()}
          </div>
        </main>
      )}
    </div>
  );
}

export default App;
