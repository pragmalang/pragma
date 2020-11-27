import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';
import { capitalize } from "../utils"

const features = [
  {
    title: 'Easy to Use',
    imageUrl: 'img/undraw_docusaurus_mountain.svg',
    description: (
      <>
        Docusaurus was designed from the ground up to be easily installed and
        used to get your website up and running quickly.
      </>
    ),
  },
  {
    title: 'Focus on What Matters',
    imageUrl: 'img/undraw_docusaurus_tree.svg',
    description: (
      <>
        Docusaurus lets you focus on your docs, and we&apos;ll do the chores. Go
        ahead and move your docs into the <code>docs</code> directory.
      </>
    ),
  },
  {
    title: 'Powered by React',
    imageUrl: 'img/undraw_docusaurus_react.svg',
    description: (
      <>
        Extend or customize your website layout by reusing React. Docusaurus can
        be extended while reusing the same header and footer.
      </>
    ),
  },
];

function Feature({ imageUrl, title, description }) {
  const imgUrl = useBaseUrl(imageUrl);
  return (
    <div className={clsx('col col--4', styles.feature)}>
      {imgUrl && (
        <div className="text--center">
          <img className={styles.featureImage} src={imgUrl} alt={title} />
        </div>
      )}
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

function Home(props) {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;
  console.log(props)
  return (
    <Layout
      title={`${siteConfig.title}: ${capitalize(siteConfig.tagline)}`}
      description={capitalize(siteConfig.tagline)}>
      <header className={clsx('hero hero--secondary', styles.heroBanner)}>
        <div className="container">
          {/* <h1 className="hero__title">{siteConfig.title}</h1> */}
          <img id="home-logo" width="400px" src="/img/full-logo.svg" />
          <p className="hero__subtitle">{capitalize(siteConfig.tagline)}</p>
          <div className={styles.buttons}>
            <Link
              className={clsx(
                'button button--outline button--primary button--lg',
                styles.getStarted,
              )}
              to={useBaseUrl('docs/')}>
              Get Started
            </Link>
          </div>
        </div>
      </header>
      <main>
        <section className={styles.snippet}>
          <img src="/img/snippet.png" width="500px" />
          <div className={styles.snippetDescriptionContainer}>
            <ol>
              <li><h3>Define Data Models</h3></li>
              <li><h3>Define Roles And Permission</h3></li>
              <li><h3>Extend CRUD Operations With Serverless Functions</h3></li>
              <li><h3>Extend Permisions With Serverless Functions</h3></li>
            </ol>
          </div>
        </section>
      </main>
    </Layout>
  );
}

export default Home;
