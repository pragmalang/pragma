const repoUrl = 'https://github.com/pragmalang/pragma'
const editUrl = `${repoUrl}/edit/master/website/`
const discordUrl = 'https://discordapp.com/invite/gbhDnfC'
const twitterUrl = 'https://twitter.com/pragmalang'

module.exports = {
  title: 'Pragma',
  tagline: 'Build Beautiful GraphQL APIs in no time',
  url: 'https://pragmalang.com',
  baseUrl: '',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.ico',
  organizationName: 'pragmalang',
  projectName: 'pragma',
  url: 'https://pragmalang.com',
  baseUrl: '/',
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
        { to: 'blog', label: 'Blog', position: 'left' },
        {
          href: discordUrl,
          label: 'Discord',
          position: 'right',
        },
        {
          href: twitterUrl,
          label: 'Twitter',
          position: 'right',
        },
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
              href: discordUrl,
            },
            {
              label: 'Twitter',
              href: twitterUrl,
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
