(() => {
    try {
        const stored = localStorage.getItem('theme');
        if (stored === 'dark' || stored === 'light') {
            document.documentElement.setAttribute('data-theme', stored);
        }
    } catch (e) {}
})();
