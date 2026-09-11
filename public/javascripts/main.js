(() => {
    const list = document.getElementById('score-list');
    const template = document.getElementById('score-template');
    const addScore = document.getElementById('add-score');
    if (!list || !template || !addScore) return;

    let nextKey = 0;
    addScore.addEventListener('click', () => {
        const key = 'n' + nextKey++;
        const wrapper = document.createElement('div');
        wrapper.innerHTML = template.innerHTML.replaceAll('__KEY__', key).trim();
        list.appendChild(wrapper.firstElementChild);
    });
    document.addEventListener('click', event => {
        const imageButton = event.target.closest('.add-image');
        if (imageButton) {
            const name = imageButton.dataset.target;
            const target = document.querySelector('[data-new-images="' + name + '"]');
            const input = document.createElement('input');
            input.type = 'text';
            input.name = name;
            input.placeholder = 'Screenshot filename';
            target.appendChild(input);
        }
        const removeButton = event.target.closest('.remove-new-score');
        if (removeButton) removeButton.closest('.new-score-row').remove();
    });
})();

(() => {
    const choices = document.querySelectorAll('[data-theme-choice]');
    if (!choices.length) return;

    const applyActiveState = () => {
        const current = document.documentElement.getAttribute('data-theme') || 'auto';
        choices.forEach(choice => {
            choice.classList.toggle('active', choice.dataset.themeChoice === current);
        });
    };

    choices.forEach(choice => {
        choice.addEventListener('click', event => {
            event.preventDefault();
            const value = choice.dataset.themeChoice;
            try {
                if (value === 'auto') {
                    localStorage.removeItem('theme');
                    document.documentElement.removeAttribute('data-theme');
                } else {
                    localStorage.setItem('theme', value);
                    document.documentElement.setAttribute('data-theme', value);
                }
            } catch (e) {}
            applyActiveState();
        });
    });

    applyActiveState();
})();

(() => {
    const box = document.getElementById('quake3-status');
    if (!box) return;

    fetch('/quake3-status.json')
        .then(response => response.ok ? response.json() : [])
        .then(servers => {
            if (!Array.isArray(servers) || servers.length === 0) return;
            box.textContent = '';

            const title = document.createElement('strong');
            title.textContent = box.dataset.quake3Title;
            box.append(title, document.createElement('br'), document.createElement('br'));

            const list = document.createElement('ul');
            list.className = 'quake3-status-list';
            servers.forEach(server => {
                const item = document.createElement('li');
                const dot = document.createElement('span');
                dot.className = 'quake3-status-dot ' + (server.online ? 'quake3-online' : 'quake3-offline');
                item.append(dot, server.label, document.createElement('br'));

                const detail = document.createElement('span');
                detail.className = 'admin-hint';
                if (server.online) {
                    detail.textContent = server.address;
                    item.append(detail);
                    if (typeof server.players === 'number') {
                        const players = document.createElement('span');
                        players.className = 'admin-hint';
                        players.textContent = server.players + (typeof server.maxPlayers === 'number' ? '/' + server.maxPlayers : '') + ' ' + box.dataset.quake3Players;
                        item.append(document.createElement('br'), players);
                    }
                } else {
                    detail.textContent = box.dataset.quake3Offline;
                    item.append(detail);
                }
                list.appendChild(item);
            });
            box.appendChild(list);
        })
        .catch(() => {});
})();
