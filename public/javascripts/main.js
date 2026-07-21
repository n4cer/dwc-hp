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
