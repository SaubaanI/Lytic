import React, { useRef } from 'react';
import './index.css';

function App() {
  const fileInputRef = useRef(null);

  const handleUploadClick = () => {
    // Trigger the hidden file input
    fileInputRef.current?.click();
  };

  const handleFileChange = (event) => {
    const file = event.target.files[0];
    if (file) {
      console.log('Selected file:', file.name);
      // Front-end only for now, back-end upload logic would go here
    }
    
    // Reset the input so the same file can be selected again if needed
    if (event.target) {
      event.target.value = '';
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
        
        <div>
          {/* Hidden file input restricted to .mp4 */}
          <input 
            type="file" 
            accept=".mp4" 
            ref={fileInputRef} 
            onChange={handleFileChange}
            style={{ display: 'none' }} 
          />
          
          <button className="btn-upload" onClick={handleUploadClick}>
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
            Upload
          </button>
        </div>
      </main>
    </div>
  );
}

export default App;
