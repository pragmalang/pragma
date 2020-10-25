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

function highlightPragmaCode() {
  // ...
}

const whiteLogo = 'http://' + window.location.host + '/src/white-logo.svg'
const purpleLogo = 'http://' + window.location.host + '/src/purple-logo.svg'

function addLinkToMenuTitle() {
  const menuBar = document.getElementById("menu-bar");
  const a = document.createElement("a");
  const title = menuBar.children.item(1).innerText
  const newMenuTitle = menuBar.children.item(1).cloneNode()
  newMenuTitle.innerText = title

  const isLightTheme = () => {
    const htmlTagClasses = document.getElementsByTagName("body").item("").classList
    return htmlTagClasses.contains("rust") || htmlTagClasses.contains("light")
  }

  const lightThemeListener = () => {
    document.getElementById("logo").setAttribute("src", purpleLogo)
  }

  const darkThemeListener = () => {
    document.getElementById("logo").setAttribute("src", whiteLogo)
  }

  const addClickListenersToThemeButtons = () => {
    const lightThemes = ["light", "rust"];
    const darkThemes = ["coal", "navy", "ayu"]
    for (let lightTheme of lightThemes) {
      document.getElementById(lightTheme).onclick = lightThemeListener
    }

    for (let darkTheme of darkThemes) {
      document.getElementById(darkTheme).onclick = darkThemeListener
    }
  }

  const logoImage = document.createElement("img")

  if (isLightTheme()) {
    logoImage.setAttribute("src", purpleLogo)
  } else {
    logoImage.setAttribute("src", whiteLogo)
  }

  logoImage.setAttribute("class", "logo-img")
  logoImage.setAttribute("id", "logo")
  addClickListenersToThemeButtons()

  a.appendChild(logoImage);
  a.setAttribute("href", "https://pragmalang.com")
  a.className = "menu-title custom-a"
  menuBar.children.item(1).insertAdjacentElement("afterend", a)
  menuBar.children.item(1).remove()
}

newTabExternalLinks();
highlightPragmaCode();
addLinkToMenuTitle();
