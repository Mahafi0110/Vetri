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
/* ============================================================
   tool-sections.js
   Handles the FAQ accordion toggle for every .faq-item on the
   page. Works for any number of FAQ blocks without extra setup.

   Include on every tool page, anywhere after the FAQ HTML:
     <script src="/js/tool-sections.js"></script>
   ============================================================ */

document.querySelectorAll('.faq-question').forEach(btn => {
    btn.addEventListener('click', () => {
        btn.closest('.faq-item').classList.toggle('open');
    });
});