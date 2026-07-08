const VetriAuth = (() => {

  /* ══════════════════════════════════════════
     CORE HELPERS
  ══════════════════════════════════════════ */
  function getToken() {
    return sessionStorage.getItem('vetri_token')
        || localStorage.getItem('vetri_token');
  }

  function getUser() {
    try {
      const raw = sessionStorage.getItem('vetri_user')
               || localStorage.getItem('vetri_user');
      return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
  }

  function isLoggedIn() {
    return !!getToken() || !!getUser();
  }

  /* ── Check admin role ── */
  function isAdminUser() {
    const user = getUser();
    if (!user) return false;
    return user.role === 'ADMIN'
        || user.role === 'ROLE_ADMIN';
  }

  function logout() {
    fetch('/api/auth/logout', { method: 'POST' })
      .finally(() => {
        sessionStorage.clear();
        localStorage.removeItem('vetri_token');
        localStorage.removeItem('vetri_user');
        window.location.href = '/login';
      });
  }

  function requireAuth() {
    if (!isLoggedIn()) {
      window.location.href = '/login';
      return false;
    }
    return true;
  }

  /* ── Build user display data ── */
  function _userData() {
    const user       = getUser();
    const loggedIn   = isLoggedIn();
    const isAdmin    = isAdminUser();

    const initials = user
      ? (user.name
          ? user.name.split(' ')
              .map(n => n[0]).join('').toUpperCase().slice(0, 2)
          : (user.email ? user.email[0].toUpperCase() : 'U'))
      : '';

    const displayName  = user?.name  || user?.email || 'My Account';
    const displayEmail = user?.email || '';
    const firstName    = displayName.split(' ')[0];

    return {
      loggedIn, isAdmin,
      initials, displayName, displayEmail, firstName
    };
  }
  function goToAdmin() {
  const token = getToken();
  if (!token) {
    window.location.href = '/login';
    return;
  }

  // Ensure cookie is set before navigating
  document.cookie =
    `vetri_token=${token}; path=/; SameSite=Lax`;

  // Navigate to admin dashboard
  window.location.href = '/admin/dashboard';
}

  /* ══════════════════════════════════════════
     renderNavRight — used by index.html
     Targets <div id="navRight">
  ══════════════════════════════════════════ */
  function renderNavRight(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const {
      loggedIn, isAdmin,
      initials, displayName, displayEmail, firstName
    } = _userData();

    if (loggedIn) {

      /* ── Admin link html — only for ADMIN role ── */
      const adminHtml = isAdmin ? `
        <button class="vf-dropdown-item vf-admin-item"
           onclick="VetriAuth.goToAdmin()">
          <i class="bi bi-shield-fill-check"
             style="color:#1E3A8A;width:18px;
                    text-align:center;"></i>
          Admin Panel
        </a>
        <div class="vf-divider"></div>` : '';

      container.innerHTML = `
        <div class="vf-nav-dropdown" id="vfNavDropdown">

          <!-- Profile Button -->
          <button class="vf-profile-btn"
                  id="vfProfileBtn"
                  onclick="VetriAuth._toggleDropdown(event)">
            <div class="vf-avatar">${initials}</div>
            <span class="vf-profile-name">${firstName}</span>
            <i class="bi bi-chevron-down vf-chevron"></i>
          </button>

          <!-- Dropdown Menu -->
          <div class="vf-dropdown-menu" id="vfDropdownMenu">

            <!-- Header with name + email -->
            <div class="vf-dropdown-header">
              <div class="vf-avatar-lg">${initials}</div>
              <div>
                <div class="vf-dh-name">${displayName}</div>
                <div class="vf-dh-email">${displayEmail}</div>
              </div>
            </div>

            <div class="vf-divider"></div>

            <!-- Admin Panel link (only ADMIN) -->
            ${adminHtml}

            <!-- Sign Out -->
            <button class="vf-dropdown-item vf-logout-item"
                    onclick="VetriAuth.logout()">
              <i class="bi bi-box-arrow-right"
                 style="width:18px;text-align:center;"></i>
              Sign Out
            </button>

          </div>
        </div>`;

      /* inject CSS once */
      _injectNavRightCSS();

      /* close on outside click */
      document.removeEventListener('click', _outsideClick);
      document.addEventListener('click', _outsideClick);

    } else {
      /* not logged in */
      container.innerHTML = `
        <div style="display:flex;align-items:center;gap:10px;">
          <a href="/login"
             style="font-size:.88rem;font-weight:600;
                    color:#3B82F6;text-decoration:none;
                    padding:7px 14px;">Login</a>
          <a href="/register"
             style="background:linear-gradient(135deg,
                      #1E3A8A,#3B82F6);
                    color:#fff;padding:9px 22px;
                    border-radius:50px;font-weight:600;
                    font-size:.88rem;text-decoration:none;">
            Get Started
          </a>
        </div>`;
    }
  }

  /* ── Toggle dropdown ── */
  function _toggleDropdown(e) {
    if (e) e.stopPropagation();
    const menu = document.getElementById('vfDropdownMenu');
    if (menu) menu.classList.toggle('open');
  }

  /* ── Close on outside click ── */
  function _outsideClick(e) {
    const wrap = document.getElementById('vfNavDropdown');
    const menu = document.getElementById('vfDropdownMenu');
    if (menu && wrap && !wrap.contains(e.target)) {
      menu.classList.remove('open');
    }
  }

  /* ── CSS for renderNavRight ── */
  function _injectNavRightCSS() {
    if (document.getElementById('vf-nav-right-css')) return;
    const s = document.createElement('style');
    s.id = 'vf-nav-right-css';
    s.textContent = `
      /* ── Profile button ── */
      .vf-profile-btn {
        display: flex; align-items: center; gap: 8px;
        background: #F3F4F6; border: 1.5px solid #E5E7EB;
        border-radius: 50px; padding: 5px 14px 5px 5px;
        cursor: pointer; font-family: 'Inter', sans-serif;
        font-size: .85rem; font-weight: 600; color: #1F2937;
        transition: border-color .2s;
      }
      .vf-profile-btn:hover { border-color: #3B82F6; }

      /* ── Avatar ── */
      .vf-avatar {
        width: 30px; height: 30px; border-radius: 50%;
        background: linear-gradient(135deg, #1E3A8A, #3B82F6);
        color: #fff; font-size: .75rem; font-weight: 700;
        display: flex; align-items: center; justify-content: center;
        flex-shrink: 0; font-family: 'Poppins', sans-serif;
      }
      .vf-avatar-lg {
        width: 40px; height: 40px; border-radius: 50%;
        background: linear-gradient(135deg, #1E3A8A, #3B82F6);
        color: #fff; font-size: .9rem; font-weight: 700;
        display: flex; align-items: center; justify-content: center;
        flex-shrink: 0; font-family: 'Poppins', sans-serif;
      }
      .vf-profile-name {
        max-width: 90px; overflow: hidden;
        text-overflow: ellipsis; white-space: nowrap;
      }
      .vf-chevron { font-size: .7rem; color: #4A5565; }

      /* ── Dropdown wrapper ── */
      .vf-nav-dropdown { position: relative; display: inline-block; }

      /* ── Dropdown menu ── */
      .vf-dropdown-menu {
        display: none;
        position: absolute; right: 0; top: calc(100% + 8px);
        background: #fff; border: 1.5px solid #E5E7EB;
        border-radius: 14px; min-width: 220px; padding: 6px;
        box-shadow: 0 12px 40px rgba(30,58,138,.12);
        z-index: 9999;
      }
      .vf-dropdown-menu.open { display: block; }

      /* ── Header ── */
      .vf-dropdown-header {
        display: flex; align-items: center; gap: 10px;
        padding: 12px; margin-bottom: 4px;
      }
      .vf-dh-name { font-weight: 700; font-size: .88rem; color: #1F2937; }
      .vf-dh-email { font-size: .75rem; color: #4A5565; margin-top: 2px; word-break: break-all; }

      /* ── Divider ── */
      .vf-divider { height: 1px; background: #E5E7EB; margin: 4px 0; }

      /* ── Dropdown item ── */
      .vf-dropdown-item {
        display: flex; align-items: center; gap: 10px;
        padding: 9px 12px; border-radius: 9px;
        font-size: .85rem; font-weight: 500; color: #1F2937;
        cursor: pointer; background: none; border: none;
        width: 100%; transition: background .15s;
        font-family: 'Inter', sans-serif;
      }
      .vf-dropdown-item:hover { background: #F3F4F6; }

      /* ── Admin item ── */
      .vf-admin-item { color: #1E3A8A !important; }
      .vf-admin-item:hover { background: #EFF6FF !important; }

      /* ── Logout item ── */
      .vf-logout-item { color: #DC2626 !important; }
      .vf-logout-item i { color: #DC2626 !important; }
      .vf-logout-item:hover { background: #FEF2F2 !important; }
    `;
    document.head.appendChild(s);
  }

  /* ══════════════════════════════════════════
     renderNavbar — used by tool pages
     Targets <div id="vetri-navbar">
  ══════════════════════════════════════════ */
  function renderNavbar(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    _injectNavbarCSS();

    const {
      loggedIn, isAdmin,
      initials, displayName, displayEmail, firstName
    } = _userData();
    const path = window.location.pathname;

    const adminItem = isAdmin ? `
      <li>
        <a class="dropdown-item d-flex align-items-center gap-2"
           href="/admin/dashboard">
          <i class="bi bi-shield-fill-check"
             style="color:#1E3A8A"></i>
          Admin Panel
        </a>
      </li>
      <li><hr class="dropdown-divider"></li>` : '';

    container.innerHTML = `
      <nav class="navbar navbar-expand-lg">
        <div class="container">
          <a class="navbar-brand" href="/">
            <div class="brand-icon">
              <i class="bi bi-file-earmark-text-fill"></i>
            </div>
            <span class="brand-name">Vetri Files</span>
          </a>
          <button class="navbar-toggler" type="button"
                  data-bs-toggle="collapse"
                  data-bs-target="#mainNav"
                  aria-controls="mainNav"
                  aria-expanded="false"
                  aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
          </button>
          <div class="collapse navbar-collapse" id="mainNav">
            <ul class="navbar-nav mx-auto">
              <li class="nav-item">
                <a class="nav-link
                   ${path === '/all-tools' ? 'active' : ''}"
                   href="/all-tools">Tools</a>
              </li>
              <li class="nav-item">
                <a class="nav-link
                   ${path === '/Feature' || path === '/features'
                     ? 'active' : ''}"
                   href="/Feature">Features</a>
              </li>
            </ul>
            <div class="d-flex align-items-center
                        gap-2 mt-2 mt-lg-0">
              ${loggedIn ? `
                <div class="dropdown">
                  <button class="vf-profile-btn"
                          id="profileDropdown"
                          data-bs-toggle="dropdown"
                          aria-expanded="false">
                    <div class="vf-avatar">${initials}</div>
                    <span class="vf-profile-name
                                 d-none d-lg-inline">
                      ${firstName}
                    </span>
                    <i class="bi bi-chevron-down vf-chevron
                               d-none d-lg-inline"></i>
                  </button>
                  <ul class="dropdown-menu
                             dropdown-menu-end
                             vf-profile-menu"
                      aria-labelledby="profileDropdown">
                    <li class="vf-menu-header">
                      <div class="vf-avatar-lg">${initials}</div>
                      <div>
                        <div class="vf-menu-name">
                          ${displayName}
                        </div>
                        <div class="vf-menu-email">
                          ${displayEmail}
                        </div>
                      </div>
                    </li>
                    <li><hr class="dropdown-divider"></li>
                    ${adminItem}
                    <li>
                      <button class="dropdown-item
                                     vf-signout-btn"
                              onclick="VetriAuth.logout()">
                        <i class="bi bi-box-arrow-right"></i>
                        Sign Out
                      </button>
                    </li>
                  </ul>
                </div>
              ` : `
                <a href="/login"
                   class="nav-link btn-login">Login</a>
                <a href="/register"
                   class="nav-link btn-get-started">
                  Get Started
                </a>
              `}
            </div>
          </div>
        </div>
      </nav>`;
  }

  /* ── CSS for renderNavbar (tool pages) ── */
  function _injectNavbarCSS() {
    if (document.getElementById('vf-navbar-css')) return;
    const s = document.createElement('style');
    s.id = 'vf-navbar-css';
    s.textContent = `
      .vf-profile-btn {
        display: flex; align-items: center; gap: 8px;
        background: none; border: 1.5px solid #E5E7EB;
        border-radius: 50px; padding: 6px 14px 6px 8px;
        cursor: pointer; transition: border-color .2s;
        font-family: inherit;
      }
      .vf-profile-btn:hover {
        border-color: #3B82F6;
        box-shadow: 0 0 0 3px rgba(59,130,246,.1);
      }
      .vf-avatar {
        width: 32px; height: 32px; border-radius: 50%;
        background: linear-gradient(135deg,#1E3A8A,#3B82F6);
        display: flex; align-items: center;
        justify-content: center;
        color: #fff; font-size: .8rem; font-weight: 700;
        flex-shrink: 0;
      }
      .vf-avatar-lg {
        width: 44px; height: 44px; border-radius: 50%;
        background: linear-gradient(135deg,#1E3A8A,#3B82F6);
        display: flex; align-items: center;
        justify-content: center;
        color: #fff; font-size: 1rem; font-weight: 700;
        flex-shrink: 0;
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
      .vf-menu-name {
        font-weight: 700; font-size: .88rem; color: #1F2937;
      }
      .vf-menu-email { font-size: .75rem; color: #6B7280; margin-top: 2px; }
      .vf-signout-btn {
        display: flex; align-items: center; gap: 8px;
        padding: 10px 12px; border-radius: 8px;
        font-size: .85rem; font-weight: 500; color: #DC2626;
        border: none; width: 100%; background: none;
        cursor: pointer; transition: background .15s;
      }
      .vf-signout-btn:hover { background: #FEF2F2; }
      .navbar .btn-login {
        font-size: .9rem; font-weight: 500;
        color: #3B82F6 !important; padding: 6px 14px !important;
        border-radius: 50px; text-decoration: none;
      }
      .navbar .btn-get-started {
        font-size: .9rem; font-weight: 600;
        background: linear-gradient(135deg,#1E3A8A,#3B82F6);
        color: #fff !important; border: none;
        padding: 10px 24px !important; border-radius: 50px;
        text-decoration: none; white-space: nowrap;
        box-shadow: 0 4px 14px rgba(59,130,246,.35);
      }
    `;
    document.head.appendChild(s);
  }

  /* ══════════════════════════════════════════
     AUTO-INIT
     Runs on every page that loads auth.js
  ══════════════════════════════════════════ */
  document.addEventListener('DOMContentLoaded', () => {
    /* tool pages */
    if (document.getElementById('vetri-navbar')) {
      renderNavbar('vetri-navbar');
    }
    /* index.html / all-tools.html */
    if (document.getElementById('navRight')) {
      renderNavRight('navRight');
    }
  });

  /* ── Public API ── */
  return {
    getToken,
    getUser,
    isLoggedIn,
    isAdminUser,
    logout,
    requireAuth,
     goToAdmin,        // ← add this
    renderNavbar,
    renderNavRight,
    _toggleDropdown
  };

})();

window.VetriAuth = VetriAuth;