import React, { useRef, useState } from 'react';
import './index.css';

function App() {
  const fileInputRef = useRef(null);

  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState('');
  const [result, setResult] = useState(null);
  const [showVideos, setShowVideos] = useState(false);
  const [videos, setVideos] = useState([]);
  const [selectedVideo, setSelectedVideo] = useState(null);

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

        {result && (
          <pre style={{ marginTop: '20px', textAlign: 'left' }}>
            {JSON.stringify(result, null, 2)}
          </pre>
        )}

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
                        <div className="thumbnail-overlay"></div>
                        <svg className="play-icon" fill="currentColor" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                        </svg>
                      </div>
                      <div className="video-info">
                        <div className="video-title">{video.name}</div>
                        <div className="video-meta">{video.date} • {video.duration}</div>
                      </div>
                    </div>
                  ))}
                </div>
                
                <div style={{ marginTop: '2rem', textAlign: 'center' }}>
                  <button 
                    className="btn-analyze" 
                    disabled={!selectedVideo}
                    onClick={() => console.log('Analyze video:', selectedVideo)}
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
    </div>
  );
}

export default App;
