/* ══════════════════════════════════════════════
   Vetri Files — File Helper
   Retrieves file from sessionStorage or IndexedDB
══════════════════════════════════════════════ */

/* ════════════════════════════════════════════
   SESSION GUARD — D_020 fix
   
   Flow:
   • Home upload sets vetri_from_upload = true
   • file-tools saveToolFile() re-sets it on every tool click
   • Tool pages call isFromUpload() — no consuming, flag stays
   • Direct navigation from all-tools → flag never set → false
════════════════════════════════════════════ */

function isFromUpload() {
  const hasFile    = !!sessionStorage.getItem('vetri_file');
  const fromUpload = sessionStorage.getItem('vetri_from_upload') === 'true';
  // return hasFile && fromUpload;
   if (hasFile && fromUpload) {
    // ✅ Consume the flag — tool page has read it once
    // file-tools will re-set it when user picks another tool
    sessionStorage.removeItem('vetri_from_upload');
    return true;
  }
  return false;
  // Flag is NOT consumed here so Back → file-tools → another tool works
}

function clearStoredFile() {
  sessionStorage.removeItem('vetri_file');
  sessionStorage.removeItem('vetri_file_data');
  sessionStorage.removeItem('vetri_storage_type');
  sessionStorage.removeItem('vetri_from_upload');
  try {
    const req = indexedDB.open('VetriFilesDB', 1);
    req.onsuccess = e => {
      const db = e.target.result;
      if (db.objectStoreNames.contains('files')) {
        db.transaction('files', 'readwrite')
          .objectStore('files')
          .delete('current_file');
      }
      db.close();
    };
  } catch (e) { /* silent */ }
}

/* ════════════════════════════════════════════
   CORE FILE RETRIEVAL
════════════════════════════════════════════ */

/* ── Get file blob ready for FormData ── */
function getFileBlob(callback) {
  const storageType = sessionStorage.getItem('vetri_storage_type');
  const stored      = sessionStorage.getItem('vetri_file');

  if (!stored) {
    alert('No file found. Please upload from home page.');
    window.location.href = '/';
    return;
  }

  const fileMeta = JSON.parse(stored);

  if (storageType === 'indexeddb') {
    _readFromDB(function(file) {
      if (file) { callback(file, fileMeta); return; }
      alert('File not found. Please upload again.');
      window.location.href = '/';
    });
  } else {
    const dataURL = sessionStorage.getItem('vetri_file_data');
    if (dataURL) {
      fetch(dataURL)
        .then(r => r.blob())
        .then(blob => {
          const file = new File([blob],
            fileMeta.name || 'file',
            { type: fileMeta.type || blob.type }
          );
          callback(file, fileMeta);
        })
        .catch(() => {
          alert('Error reading file. Please upload again.');
          window.location.href = '/';
        });
    } else {
      _readFromDB(function(file) {
        if (file) { callback(file, fileMeta); return; }
        alert('File not found. Please upload again.');
        window.location.href = '/';
      });
    }
  }
}

/* ── Get dataURL for preview (images only) ── */
function getFileDataURL(callback) {
  const storageType = sessionStorage.getItem('vetri_storage_type');

  if (storageType === 'indexeddb') {
    _readFromDB(function(file) {
      if (!file) { callback(null); return; }
      const reader = new FileReader();
      reader.onload = e => callback(e.target.result);
      reader.readAsDataURL(file);
    });
  } else {
    const dataURL = sessionStorage.getItem('vetri_file_data');
    if (dataURL) { callback(dataURL); return; }
    _readFromDB(function(file) {
      if (!file) { callback(null); return; }
      const reader = new FileReader();
      reader.onload = e => callback(e.target.result);
      reader.readAsDataURL(file);
    });
  }
}

/* ── Get video/audio object URL for player ── */
function getFileObjectURL(callback) {
  const storageType = sessionStorage.getItem('vetri_storage_type');
  const dataURL     = sessionStorage.getItem('vetri_file_data');

  const tryIndexedDB = cb => {
    _readFromDB(function(file) {
      cb(file ? URL.createObjectURL(file) : null);
    });
  };

  const trySessionStorage = cb => {
    if (!dataURL) { cb(null); return; }
    fetch(dataURL)
      .then(r => r.blob())
      .then(blob => cb(URL.createObjectURL(blob)))
      .catch(() => cb(null));
  };

  if (storageType === 'indexeddb') {
    tryIndexedDB(url => {
      if (url) { callback(url); } else { trySessionStorage(callback); }
    });
  } else if (storageType === 'session') {
    trySessionStorage(url => {
      if (url) { callback(url); } else { tryIndexedDB(callback); }
    });
  } else {
    if (dataURL) {
      trySessionStorage(url => {
        if (url) { callback(url); } else { tryIndexedDB(callback); }
      });
    } else {
      tryIndexedDB(callback);
    }
  }
}

/* ── Internal: read file from IndexedDB ── */
function _readFromDB(callback) {
  try {
    const req = indexedDB.open('VetriFilesDB', 1);
    req.onerror = () => callback(null);
    req.onupgradeneeded = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains('files')) {
        db.createObjectStore('files', { keyPath: 'id' });
      }
    };
    req.onsuccess = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains('files')) {
        db.close(); callback(null); return;
      }
      const getReq = db.transaction('files', 'readonly')
                       .objectStore('files').get('current_file');
      getReq.onsuccess = ev => {
        db.close();
        const record = ev.target.result;
        callback(record && record.file ? record.file : null);
      };
      getReq.onerror = () => { db.close(); callback(null); };
    };
  } catch(e) { callback(null); }
}

/* ── Format time helper ── */
function formatTime(secs) {
  if (isNaN(secs) || secs < 0) return '00:00.000';
  const h  = Math.floor(secs / 3600);
  const m  = Math.floor((secs % 3600) / 60);
  const s  = Math.floor(secs % 60);
  const ms = Math.round((secs % 1) * 1000);
  const hStr = h > 0 ? String(h).padStart(2,'0') + ':' : '';
  return hStr +
    String(m).padStart(2,'0') + ':' +
    String(s).padStart(2,'0') + '.' +
    String(ms).padStart(3,'0');
}

/* ── Format file size ── */
function formatSize(bytes) {
  if (!bytes) return '—';
  if (bytes < 1024)                return bytes + ' B';
  if (bytes < 1024 * 1024)        return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}