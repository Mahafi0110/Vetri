/* ══════════════════════════════════════════════
   Vetri Files — Error Helper
   Include in every tool page:
   <script src="/js/vetri-error-helper.js"></script>
══════════════════════════════════════════════ */

/* ── Inject error modal HTML into page automatically ── */
(function injectErrorModal() {
    const modalHTML = `
    <div class="modal-overlay" id="vetriErrorModal" style="
        position:fixed;inset:0;
        background:rgba(30,30,30,.55);
        display:flex;align-items:center;justify-content:center;
        z-index:9999;opacity:0;pointer-events:none;transition:opacity .3s;">
        <div style="
            background:#fff;border-radius:24px;
            padding:44px 36px 40px;max-width:420px;width:90%;
            text-align:center;box-shadow:0 24px 64px rgba(0,0,0,.18);
            transform:scale(.95);transition:transform .3s;"
            id="vetriErrorBox">

            <!-- Red icon -->
            <div style="
                width:72px;height:72px;border-radius:50%;
                background:linear-gradient(135deg,#DC2626,#EF4444);
                display:flex;align-items:center;justify-content:center;
                margin:0 auto 20px;
                box-shadow:0 6px 20px rgba(220,38,38,.3);">
                <i class="bi bi-exclamation-triangle-fill"
                   style="color:#fff;font-size:2rem;"></i>
            </div>

            <!-- Title -->
            <p id="vetriErrorTitle" style="
                font-family:'Poppins',sans-serif;font-size:1.25rem;
                font-weight:800;color:#1F2937;margin-bottom:10px;">
                Something went wrong
            </p>

            <!-- Message -->
            <p id="vetriErrorMessage" style="
                font-size:.95rem;font-weight:600;
                color:#1F2937;margin-bottom:8px;">
                Could not process your file.
            </p>

            <!-- Hint -->
            <p id="vetriErrorHint" style="
                font-size:.85rem;color:#4A5565;margin-bottom:28px;
                line-height:1.6;">
                Please try again.
            </p>

            <!-- Go Back button -->
            <button onclick="vetriCloseError()" style="
                width:100%;padding:14px;margin-bottom:10px;
                font-family:'Poppins',sans-serif;font-size:1rem;
                font-weight:700;
                background:linear-gradient(135deg,#1E3A8A,#3B82F6);
                color:#fff;border:none;border-radius:12px;cursor:pointer;
                box-shadow:0 4px 18px rgba(59,130,246,.35);">
                ← Go Back &amp; Try Again
            </button>

            <!-- Upload New File button -->
            <button onclick="window.location.href='/'" style="
                width:100%;padding:13px;
                font-family:'Poppins',sans-serif;font-size:.95rem;
                font-weight:600;background:#fff;color:#1F2937;
                border:1.5px solid #E5E7EB;border-radius:12px;cursor:pointer;">
                ⇦ Upload New File
            </button>
        </div>
    </div>`;

    // Inject into body when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            document.body.insertAdjacentHTML('beforeend', modalHTML);
            _initErrorModalStyles();
        });
    } else {
        document.body.insertAdjacentHTML('beforeend', modalHTML);
        _initErrorModalStyles();
    }
})();

/* ── Animate modal open/close ── */
function _initErrorModalStyles() {
    const overlay = document.getElementById('vetriErrorModal');
    if (!overlay) return;
    overlay.addEventListener('click', function(e) {
        if (e.target === overlay) vetriCloseError();
    });
}

/* ── Show error modal ── */
function showErrorModal(title, message, hint) {
    // Hide processing modal if open
    const processing = document.getElementById('processingModal');
    if (processing) processing.classList.remove('show');

    document.getElementById('vetriErrorTitle').textContent   = title;
    document.getElementById('vetriErrorMessage').textContent = message;
    document.getElementById('vetriErrorHint').textContent    = hint || '';

    const overlay = document.getElementById('vetriErrorModal');
    const box     = document.getElementById('vetriErrorBox');
    overlay.style.opacity       = '1';
    overlay.style.pointerEvents = 'all';
    box.style.transform         = 'scale(1)';
}

/* ── Close error modal ── */
function vetriCloseError() {
    const overlay = document.getElementById('vetriErrorModal');
    const box     = document.getElementById('vetriErrorBox');
    overlay.style.opacity       = '0';
    overlay.style.pointerEvents = 'none';
    box.style.transform         = 'scale(.95)';
}

/* ══════════════════════════════════════════════
   FILE TYPE VALIDATORS
   Call these before sending to API
══════════════════════════════════════════════ */

function vetriValidatePdf(meta) {
    const name = (meta.name || '').toLowerCase();
    const type = (meta.type || '').toLowerCase();
    if (name.endsWith('.pdf') || type === 'application/pdf') return true;
    showErrorModal(
        'Wrong File Type',
        'Only PDF files are supported for this tool.',
        'Please go back and upload a valid PDF file.'
    );
    return false;
}

function vetriValidateImage(meta) {
    const name = (meta.name || '').toLowerCase();
    const type = (meta.type || '').toLowerCase();
    const imgExts = ['jpg','jpeg','png','gif','webp','bmp','tiff','tif','avif'];
    const valid = type.startsWith('image/') ||
                  imgExts.some(e => name.endsWith('.' + e));
    if (valid) return true;
    showErrorModal(
        'Wrong File Type',
        'Only image files are supported for this tool.',
        'Please go back and upload an image file (JPG, PNG, WEBP, etc.).'
    );
    return false;
}

function vetriValidateAudio(meta) {
    const name = (meta.name || '').toLowerCase();
    const type = (meta.type || '').toLowerCase();
    const audioExts = ['mp3','wav','ogg','aac','m4a','flac','wma','opus','aiff'];
    const valid = type.startsWith('audio/') ||
                  audioExts.some(e => name.endsWith('.' + e));
    if (valid) return true;
    showErrorModal(
        'Wrong File Type',
        'Only audio files are supported for this tool.',
        'Please go back and upload an audio file (MP3, WAV, AAC, etc.).'
    );
    return false;
}

function vetriValidateVideo(meta) {
    const name = (meta.name || '').toLowerCase();
    const type = (meta.type || '').toLowerCase();
    const videoExts = ['mp4','avi','mov','mkv','wmv','flv','webm','m4v','mpeg','mpg'];
    const valid = type.startsWith('video/') ||
                  videoExts.some(e => name.endsWith('.' + e));
    if (valid) return true;
    showErrorModal(
        'Wrong File Type',
        'Only video files are supported for this tool.',
        'Please go back and upload a video file (MP4, MOV, AVI, etc.).'
    );
    return false;
}

/* ══════════════════════════════════════════════
   HTTP ERROR HANDLER
   Pass the response status to get friendly message
══════════════════════════════════════════════ */

function vetriHandleHttpError(status, toolName) {
    toolName = toolName || 'your file';

    if (status === 400) {
        showErrorModal(
            'Invalid File',
            'The uploaded file could not be processed.',
            'Please make sure your file is not corrupted and try again.'
        );
    } else if (status === 401) {
        showErrorModal(
            'Incorrect Password',
            'The password you entered does not match this file.',
            'Please check your password and try again.'
        );
    } else if (status === 413) {
        showErrorModal(
            'File Too Large',
            'Your file is too large to process right now.',
            'Try compressing the file first, then use this tool again.'
        );
    } else if (status === 415) {
        showErrorModal(
            'Wrong File Type',
            'This tool does not support the uploaded file format.',
            'Please go back and upload the correct file type.'
        );
    } else if (status === 500) {
        showErrorModal(
            'Processing Failed',
            'Something went wrong on our end while processing ' + toolName + '.',
            'This sometimes happens with complex files. Please try again with a different file.'
        );
    } else {
        showErrorModal(
            'Something Went Wrong',
            'We could not complete this action right now.',
            'Please go back, upload your file again, and try once more.'
        );
    }
}