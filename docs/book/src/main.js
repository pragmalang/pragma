function newTabExternalLinks() {
  const links = document.getElementsByTagName("a");
  Array.from(links)
    .filter(
      link =>
        new URL(link.origin).hostname !==
        new URL(document.location.origin).hostname
    )
    .forEach(link => link.setAttribute("target", "_blank"));
}

function highlightHeavenlyCode() {
  // ...
}

function addLinkToMenuTitle() {
  const menuBar = document.getElementById("menu-bar-sticky-container");
  const a = document.createElement("a");
  const title = menuBar.children.item(1).innerText
  const newMenuTitle = menuBar.children.item(1).cloneNode()
  newMenuTitle.innerText = title

  const logoImage = document.createElement("img")
  logoImage.setAttribute("src", "/purple-logo.png")
  logoImage.setAttribute("class", "logo-img")

  a.appendChild(newMenuTitle);
  a.setAttribute("href", "/")
  a.className = "menu-title custom-a"
  menuBar.children.item(1).insertAdjacentElement("afterend",a)
  menuBar.children.item(1).remove()
}

newTabExternalLinks();
highlightHeavenlyCode();
addLinkToMenuTitle();
