function newTabExternalLinks() {
    const links = document.getElementsByTagName('a')
    Array.from(links)
        .filter(link => new URL(link.origin).hostname !== new URL(document.location.origin).hostname)
        .forEach(link => link.setAttribute('target', '_blank'))
}

function highlightHeavenlyCode() {
    // ...
}

newTabExternalLinks()
highlightHeavenlyCode()