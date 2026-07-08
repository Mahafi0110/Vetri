const VetriAuth = (() => {

  function getToken() {
    return sessionStorage.getItem('vetri_token') || localStorage.getItem('vetri_token');
  }

  function getUser() {
    try {
      const raw = sessionStorage.getItem('vetri_user') || localStorage.getItem('vetri_user');
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }

  function isLoggedIn() {
    // ✅ Check token first, fall back to user object
    // This handles cases where token wasn't saved but user was
    return !!getToken() || !!getUser();
  }

  function logout() {
    fetch('/api/auth/logout', { method: 'POST' }).finally(() => {
      sessionStorage.removeItem('vetri_token');
      sessionStorage.removeItem('vetri_user');
      localStorage.removeItem('vetri_token');
      localStorage.removeItem('vetri_user');
      window.location.href = '/login';
    });
  }

  function requireAuth() {
    if (!isLoggedIn()) { window.location.href = '/login'; return false; }
    return true;
  }

  /* ── shared helper to build user data ── */
  function _userData() {
    const user        = getUser();
    const loggedIn    = isLoggedIn();
    const initials    = user
      ? (user.name
          ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
          : (user.email ? user.email[0].toUpperCase() : 'U'))
      : '';
    const displayName  = user?.name  || user?.email || 'My Account';
    const displayEmail = user?.email || '';
    const firstName    = displayName.split(' ')[0];
    return { loggedIn, initials, displayName, displayEmail, firstName };
  }

  /* ════════════════════════════════════════════════════
     renderNavbar('vetri-navbar')
     For ALL tool pages — replaces the <div id="vetri-navbar">
     with a full Bootstrap navbar including login state.
     Also injects the required CSS so tool pages don't need it.
  ════════════════════════════════════════════════════ */
  function renderNavbar(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    _injectNavbarCSS(); // inject profile button CSS if not already present

    const { loggedIn, initials, displayName, displayEmail, firstName } = _userData();
    const path = window.location.pathname;

    container.innerHTML = `
      <nav class="navbar navbar-expand-lg">
        <div class="container">
          <a class="navbar-brand" href="/">
            <div class="brand-icon"><i class="bi bi-file-earmark-text-fill"></i></div>
            <span class="brand-name">Vetri Files</span>
          </a>
          <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav"
                  aria-controls="mainNav" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
          </button>
          <div class="collapse navbar-collapse" id="mainNav">
            <ul class="navbar-nav mx-auto">
              <li class="nav-item">
                <a class="nav-link ${path === '/all-tools' ? 'active' : ''}" href="/all-tools">Tools</a>
              </li>
              <li class="nav-item">
                <a class="nav-link ${path === '/Feature' || path === '/features' ? 'active' : ''}" href="/Feature">Features</a>
              </li>
            </ul>
            <div class="d-flex align-items-center gap-2 mt-2 mt-lg-0">
              ${loggedIn ? `
                <div class="dropdown">
                  <button class="vf-profile-btn" id="profileDropdown"
                          data-bs-toggle="dropdown" aria-expanded="false">
                    <div class="vf-avatar">${initials}</div>
                    <span class="vf-profile-name d-none d-lg-inline">${firstName}</span>
                    <i class="bi bi-chevron-down vf-chevron d-none d-lg-inline"></i>
                  </button>
                  <!-- Only shows if user role is ADMIN -->
<div id="adminMenuLink" style="display:none">
  <div class="dropdown-divider"></div>
  <a class="dropdown-item" href="/admin/dashboard">
    <i class="bi bi-shield-fill-check me-2"
       style="color:#1E3A8A"></i>
    Admin Panel
  </a>
</div>
                  <ul class="dropdown-menu dropdown-menu-end vf-profile-menu"
                      aria-labelledby="profileDropdown">
                    <li class="vf-menu-header">
                      <div class="vf-avatar-lg">${initials}</div>
                      <div>
                        <div class="vf-menu-name">${displayName}</div>
                        <div class="vf-menu-email">${displayEmail}</div>
                      </div>
                    </li>
                    <li><hr class="dropdown-divider"></li>
                    <li>
                      <button class="dropdown-item vf-signout-btn"
                              onclick="VetriAuth.logout()">
                        <i class="bi bi-box-arrow-right"></i> Sign Out
                      </button>
                    </li>
                  </ul>
                </div>
              ` : `
                <a href="/login"    class="nav-link btn-login">Login</a>
                <a href="/register" class="nav-link btn-get-started">Get Started</a>
              `}
            </div>
          </div>
        </div>
      </nav>`;
  }

  /* ════════════════════════════════════════════════════
     renderNavRight('navRight')
     For index.html and all-tools.html which use the
     custom nav-inner structure with id="navRight".
  ════════════════════════════════════════════════════ */
  function renderNavRight(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const { loggedIn, initials, displayName, displayEmail, firstName } = _userData();

    if (loggedIn) {
      container.innerHTML = `
        <div class="dropdown" style="position:relative">
          <button class="profile-btn" onclick="VetriAuth._toggleDropdown()">
            <div class="avatar">${initials}</div>
            <span class="profile-name">${firstName}</span>
            <i class="bi bi-chevron-down" style="font-size:.7rem;color:var(--sub,#4A5565)"></i>
          </button>
          <div class="dropdown-menu-custom" id="profileMenu">
            <div class="dropdown-header">
              <div class="avatar-lg">${initials}</div>
              <div>
                <div class="dh-name">${displayName}</div>
                <div class="dh-email">${displayEmail}</div>
              </div>
            </div>
            <button class="dropdown-item-custom logout" onclick="VetriAuth.logout()">
              <i class="bi bi-box-arrow-right"></i> Sign Out
            </button>
          </div>
        </div>`;

      // Close on outside click
      document.addEventListener('click', e => {
        const menu = document.getElementById('profileMenu');
        if (menu && !e.target.closest('.dropdown')) menu.classList.remove('open');
      });

    } else {
      container.innerHTML = `
        <div style="display:flex;align-items:center;gap:10px;">
          <a href="/login" style="font-size:.88rem;font-weight:600;color:#3B82F6;text-decoration:none;padding:7px 14px;">Login</a>
          <a href="/register" style="background:linear-gradient(135deg,#1E3A8A,#3B82F6);color:#fff;padding:9px 22px;border-radius:50px;font-weight:600;font-size:.88rem;text-decoration:none;">Get Started</a>
        </div>`;
    }
  }

  function _toggleDropdown() {
    const menu = document.getElementById('profileMenu');
    if (menu) menu.classList.toggle('open');
  }

  /* ════════════════════════════════════════════════════
     _injectNavbarCSS()
     Injects profile button styles into the page once.
     Uses vf- prefix to avoid clashing with existing CSS.
     Called automatically by renderNavbar().
  ════════════════════════════════════════════════════ */
  function _injectNavbarCSS() {
    if (document.getElementById('vf-navbar-css')) return; // already injected
    const style = document.createElement('style');
    style.id = 'vf-navbar-css';
    style.textContent = `
      .vf-profile-btn {
        display: flex; align-items: center; gap: 8px;
        background: none; border: 1.5px solid #E5E7EB;
        border-radius: 50px; padding: 6px 14px 6px 8px;
        cursor: pointer; transition: border-color .2s, box-shadow .2s;
        font-family: inherit;
      }
      .vf-profile-btn:hover {
        border-color: #3B82F6;
        box-shadow: 0 0 0 3px rgba(59,130,246,.1);
      }
      .vf-avatar {
        width: 32px; height: 32px; border-radius: 50%;
        background: linear-gradient(135deg, #1E3A8A, #3B82F6);
        display: flex; align-items: center; justify-content: center;
        color: #fff; font-size: .8rem; font-weight: 700; flex-shrink: 0;
      }
      .vf-avatar-lg {
        width: 44px; height: 44px; border-radius: 50%;
        background: linear-gradient(135deg, #1E3A8A, #3B82F6);
        display: flex; align-items: center; justify-content: center;
        color: #fff; font-size: 1rem; font-weight: 700; flex-shrink: 0;
      }
      .vf-profile-name {
        font-size: .88rem; font-weight: 600; color: #1F2937;
      }
      .vf-chevron { font-size: .7rem; color: #6B7280; }
      .vf-profile-menu {
        border: 1.5px solid #E5E7EB; border-radius: 14px;
        padding: 8px; box-shadow: 0 8px 32px rgba(0,0,0,.12);
        min-width: 230px;
      }
      .vf-menu-header {
        display: flex; align-items: center; gap: 12px;
        padding: 10px 12px 14px; list-style: none;
      }
      .vf-menu-name { font-weight: 700; font-size: .88rem; color: #1F2937; }
      .vf-menu-email { font-size: .75rem; color: #6B7280; margin-top: 2px; }
      .vf-signout-btn {
        display: flex; align-items: center; gap: 8px;
        padding: 10px 12px; border-radius: 8px; font-size: .85rem;
        font-weight: 500; color: #DC2626; border: none; width: 100%;
        background: none; cursor: pointer; transition: background .15s;
      }
      .vf-signout-btn:hover { background: #FEF2F2; }

      /* btn-login and btn-get-started for logged-out state in tool pages */
      .navbar .btn-login {
        font-size: .9rem; font-weight: 500;
        color: #3B82F6 !important; padding: 6px 14px !important;
        border-radius: 50px; text-decoration: none;
      }
      .navbar .btn-get-started {
        font-size: .9rem; font-weight: 600;
        background: linear-gradient(135deg, #1E3A8A, #3B82F6);
        color: #fff !important; border: none;
        padding: 10px 24px !important; border-radius: 50px;
        text-decoration: none; white-space: nowrap;
        box-shadow: 0 4px 14px rgba(59,130,246,.35);
      }
    `;
    document.head.appendChild(style);
  }

  return {
    getToken, getUser, isLoggedIn, logout, requireAuth,
    renderNavbar,    // tool pages  → <div id="vetri-navbar">
    renderNavRight,  // index/all-tools → <div id="navRight">
    _toggleDropdown
  };

})();

window.VetriAuth = VetriAuth;

/* ════════════════════════════════════════════════════
   AUTO-INIT — runs on every page that loads auth.js
   No extra script needed on any page.

   Tool pages:        add <div id="vetri-navbar"></div>
   index/all-tools:   keep <div id="navRight"></div>
════════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('vetri-navbar')) {
    VetriAuth.renderNavbar('vetri-navbar');
  }
  if (document.getElementById('navRight')) {
    VetriAuth.renderNavRight('navRight');
  }
});
  // Show admin link only if role is ADMIN
  window.addEventListener('DOMContentLoaded', () => {
    const user = JSON.parse(
      sessionStorage.getItem('vetri_user') || '{}'
    );
    if (user.role === 'ADMIN') {
      document.getElementById('adminMenuLink')
        .style.display = 'block';
    }
  });
