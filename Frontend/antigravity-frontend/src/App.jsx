import React, { useRef, useState } from 'react';
import './index.css';

const parseAnalysis = (result) => {
  if (!result) return null;
  console.log("Raw analysis result to parse:", result);
  
  let parsed = result;

  // If it's a string, aggressively extract the JSON
  if (typeof parsed === 'string') {
    try {
      // First, attempt to strip markdown blocks
      let cleanStr = parsed.replace(/```json/gi, '').replace(/```/g, '').trim();
      
      // Secondary fallback: Extract strictly between { and } in case LLM added conversational text
      const firstBrace = cleanStr.indexOf('{');
      const lastBrace = cleanStr.lastIndexOf('}');
      if (firstBrace !== -1 && lastBrace !== -1 && lastBrace >= firstBrace) {
          cleanStr = cleanStr.substring(firstBrace, lastBrace + 1);
      }

      parsed = JSON.parse(cleanStr);
      
      // If double-encoded, do it one more time
      if (typeof parsed === 'string') {
        cleanStr = parsed.replace(/```json/gi, '').replace(/```/g, '').trim();
        const fb = cleanStr.indexOf('{');
        const lb = cleanStr.lastIndexOf('}');
        if (fb !== -1 && lb !== -1 && lb >= fb) {
            cleanStr = cleanStr.substring(fb, lb + 1);
        }
        parsed = JSON.parse(cleanStr);
      }
    } catch (e) {
      console.error("Failed to parse analysis result string:", e, "Raw string was:", result);
      return null;
    }
  }

  if (typeof parsed !== 'object' || parsed === null) {
      console.error("Parsed result is not an object:", parsed);
      return null;
  }
  
  console.log("Successfully parsed object:", parsed);
  return parsed;
};

const AnalysisDashboard = ({ data }) => {
  // Normalize keys to support both camelCase and PascalCase from backend
  const normalizedData = {};
  if (data) {
    Object.keys(data).forEach(key => {
      const normalizedKey = key.charAt(0).toLowerCase() + key.slice(1);
      normalizedData[normalizedKey] = data[key];
    });
  }

  const {
    overallScore,
    hookScore,
    clarityScore,
    pacingScore,
    ctaScore,
    strongMoments,
    weakMoments,
    strongPoints,
    lowPoints,
    suggestions,
    keyInsight
  } = normalizedData;

  const radius = 88;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = overallScore ? circumference - (overallScore / 100) * circumference : circumference;

  return (
    <div className="analysis-results-container">
      {/* Overall Score Section */}
      <div className="overall-score-section">
        <h3 className="overall-score-title">Overall Match Score</h3>
        <div className="circular-progress">
          <svg width="200" height="200" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
            <circle cx="100" cy="100" r={radius} className="circle-bg" />
            <circle
              cx="100"
              cy="100"
              r={radius}
              className="circle-progress"
              style={{ strokeDasharray: circumference, strokeDashoffset }}
            />
          </svg>
          <div className="progress-text-container">
            <span className="progress-value">{overallScore || 0}</span>
            <span className="progress-label">out of 100</span>
          </div>
        </div>

        {/* Sub-scores */}
        <div className="sub-scores-grid">
          <div className="sub-score-card">
            <div className="sub-score-value">{hookScore || 0}/10</div>
            <div className="sub-score-label">Hook</div>
          </div>
          <div className="sub-score-card">
            <div className="sub-score-value">{clarityScore || 0}/10</div>
            <div className="sub-score-label">Clarity</div>
          </div>
          <div className="sub-score-card">
            <div className="sub-score-value">{pacingScore || 0}/10</div>
            <div className="sub-score-label">Pacing</div>
          </div>
          <div className="sub-score-card">
            <div className="sub-score-value">{ctaScore || 0}/10</div>
            <div className="sub-score-label">CTA</div>
          </div>
        </div>
      </div>

      {/* Key Insight */}
      {keyInsight && (
        <div className="key-insight-banner">
          <svg className="key-insight-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          <div className="key-insight-content">
            <h3>Key Insight</h3>
            <p>{keyInsight}</p>
          </div>
        </div>
      )}

      {/* Grid for Points & Suggestions */}
      <div className="details-grid">
        <div className="detail-card">
          <h3 className="detail-title positive">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{width: '24px', height: '24px'}}><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
            What Worked Well
          </h3>
          <ul className="detail-list positive">
            {(strongPoints || []).map((point, i) => (
              <li key={i} className="detail-item">
                <svg className="detail-item-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 16 16 12 12 8"></polyline><line x1="8" y1="12" x2="16" y2="12"></line></svg>
                {point}
              </li>
            ))}
          </ul>
        </div>

        <div className="detail-card">
          <h3 className="detail-title negative">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{width: '24px', height: '24px'}}><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>
            Areas for Improvement
          </h3>
          <ul className="detail-list negative">
            {(lowPoints || []).map((point, i) => (
              <li key={i} className="detail-item">
                <svg className="detail-item-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="16"></line><line x1="8" y1="12" x2="16" y2="12"></line></svg>
                {point}
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="details-grid">
        <div className="detail-card" style={{ gridColumn: '1 / -1' }}>
          <h3 className="detail-title neutral">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{width: '24px', height: '24px'}}><circle cx="12" cy="12" r="10"></circle><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
            Recommendations
          </h3>
          <ul className="detail-list neutral">
            {(suggestions || []).map((point, i) => (
              <li key={i} className="detail-item">
                <svg className="detail-item-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 16 16 12 12 8"></polyline><line x1="8" y1="12" x2="16" y2="12"></line></svg>
                {point}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* Moments */}
      <div className="details-grid">
        <div className="detail-card">
          <h3 className="detail-title positive">Strong Moments</h3>
          <div>
            {(strongMoments || []).map((m, i) => (
              <div key={i} className="moment-card strong">
                <div className="moment-header">
                  <span>Timestamp</span>
                  <span>{m.timestamp}</span>
                </div>
                <div className="moment-desc">{m.description}</div>
                <div className="moment-reason">{m.reason}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="detail-card">
          <h3 className="detail-title negative">Weak Moments</h3>
          <div>
            {(weakMoments || []).map((m, i) => (
              <div key={i} className="moment-card weak">
                <div className="moment-header">
                  <span>Timestamp</span>
                  <span>{m.timestamp}</span>
                </div>
                <div className="moment-desc">{m.description}</div>
                <div className="moment-reason">{m.reason}</div>
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
    // Trigger the hidden file input
    fileInputRef.current?.click();
  };

  const handleFileChange = async (event) => {
    const file = event.target.files[0];
    if (!file) {
      return;

    }
    console.log('Selected file:', file.name);

    const formData = new FormData();
    formData.append('file', file);

    try {
      setUploading(true);
      setMessage('Uploading...');
      setResult(null);
      const response = await fetch('http://127.0.0.1:8000/upload-ad', {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.detail || 'Upload Failed');
      }

      setMessage('Upload successful');
      setTimeout(() => {
        setMessage('');
      }, 3000);

      console.log('Backend  response:', data);
    } catch (error) {
      console.error('Upload error:', error);
      setMessage(`Upload failed: ${error.message}`);
    } finally {
      setUploading(false);

      if (event.target) {
        event.target.value = '';
      }
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
          {/* Hidden file input restricted to .mp4 */}
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
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"
              />
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
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
              />
            </svg>
            {showVideos ? 'Hide Videos' : 'Select Video'}
          </button>
        </div>
        {message && <p style={{ marginTop: '20px' }}>{message}</p>}

        {/* Video Selection Grid */}
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
                        console.log('Starting analysis for ad:', ad_id);
                        const response = await fetch('http://127.0.0.1:8000/analysis/start', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ ad_id })
                        });
                        const data = await response.json();
                        console.log('Analysis start response:', data);
                        setAnalysisResult(data);
                      } catch (error) {
                        console.error('Failed to start analysis:', error);
                      } finally {
                        setAnalysisLoading(false);
                      }
                    }}
                  >
                    <svg
                      className="upload-icon"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      xmlns="http://www.w3.org/2000/svg"
                    >
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
          <div style={{ width: '100%', display: 'flex', justifyContent: 'flex-start', marginBottom: '2rem' }}>
            <button className="btn-secondary" onClick={() => setView('home')} style={{ padding: '0.75rem 1.5rem', fontSize: '1rem' }}>
              <svg 
                className="upload-icon" 
                fill="none" 
                stroke="currentColor" 
                viewBox="0 0 24 24"
                xmlns="http://www.w3.org/2000/svg"
                style={{ width: '20px', height: '20px' }}
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
              Back
            </button>
          </div>
          <h2 className="section-title" style={{ textAlign: 'center', fontSize: '2.5rem', marginBottom: '2rem' }}>Analysis</h2>
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
          
          <div style={{ marginTop: '2rem', width: '100%', maxWidth: '900px', margin: '2rem auto', color: 'var(--text-color)' }}>
            {analysisLoading && <p style={{ textAlign: 'center' }}>Loading analysis results...</p>}
            {analysisResult && (() => {
              const parsedAnalysis = parseAnalysis(analysisResult);
              if (!parsedAnalysis || Object.keys(parsedAnalysis).length === 0) {
                return (
                  <div style={{ background: 'var(--bg-secondary, #1a1b1e)', padding: '1.5rem', borderRadius: '12px', border: '1px solid var(--border-color, #2c2e33)' }}>
                    <h3 style={{ marginTop: 0, marginBottom: '1rem' }}>Raw Final Analysis (Unparsable)</h3>
                    <pre style={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: '14px', margin: 0 }}>
                      {typeof analysisResult === 'string' ? analysisResult : JSON.stringify(analysisResult, null, 2)}
                    </pre>
                  </div>
                );
              }
              return <AnalysisDashboard data={parsedAnalysis} />;
            })()}
          </div>
        </main>
      )}
    </div>
  );
}

export default App;
