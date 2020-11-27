const repoUrl = 'https://github.com/pragmalang/pragma'
const editUrl = `${repoUrl}/edit/master/website/`

module.exports = {
  title: 'Pragma',
  tagline: 'Build Beautiful GraphQL APIs in no time',
  url: 'https://pragmalang.com',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.ico',
  organizationName: 'pragmalang', // Usually your GitHub org/user name.
  projectName: 'pragma', // Usually your repo name.
  themeConfig: {
    defaultMode: "dark",
    prism: {
      additionalLanguages: ['haskell'],
    },
    hideableSidebar: true,
    navbar: {
      title: 'Pragma',
      logo: {
        alt: 'Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          to: 'docs/',
          activeBasePath: 'docs',
          label: 'Docs',
          position: 'left',
        },
        {to: 'blog', label: 'Blog', position: 'left'},
        {
          href: repoUrl,
          label: 'GitHub',
          position: 'right',
        },
        { to: 'faq', label: 'FAQ', position: 'left' }
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Introduction',
              to: 'docs/',
            },
            {
              label: 'Install Pragma',
              to: 'docs/getting-started/install-pragma',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'Discord',
              href: 'https://discordapp.com/invite/gbhDnfC',
            },
            {
              label: 'Twitter',
              href: 'https://twitter.com/pragmalang',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'Blog',
              to: 'blog',
            },
            {
              label: 'GitHub',
              href: repoUrl,
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Heavenly-x, Inc.`,
    },
  },
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: editUrl,
        },
        blog: {
          showReadingTime: true,
          editUrl: editUrl,
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],
};
