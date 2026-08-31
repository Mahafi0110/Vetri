/* ============================================================
   common-nav.js
   Handles ONLY the mobile hamburger toggle for the shared navbar
   fragment. Login state / profile dropdown / admin link / logout
   are already handled by your existing /js/auth.js (VetriAuth),
   which auto-populates #navRight — do not duplicate that here.

   Include on every page that includes navbar.html, AFTER auth.js:
     <script src="/js/auth.js"></script>
     <script src="/js/common-nav.js"></script>
   ============================================================ */

function toggleNavLinks() {
    const links = document.getElementById('navLinks');
    if (links) links.classList.toggle('open');
}

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.nav-links a').forEach(link => {
        link.addEventListener('click', () => {
            const links = document.getElementById('navLinks');
            if (links) links.classList.remove('open');
        });
    });
});

window.addEventListener('resize', () => {
    if (window.innerWidth >= 576) {
        const links = document.getElementById('navLinks');
        if (links) links.classList.remove('open');
    }
});