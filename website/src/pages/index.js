import React, { useEffect, useState } from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';
import { capitalize } from "../utils";

function ImageCard({ buttonText, imageUrl, url, isExternal }) {
  const imgUrl = useBaseUrl(imageUrl)
  return (
    <div className={clsx('col col--4', styles.imageCard)}>
      <div className="text--center">
        <a href={url} target={isExternal && "__blank"}>
          <img src={imgUrl} className={styles.imageCardImage} alt={buttonText} />
        </a>
      </div>
      <a className="button button--primary button--outline button--lg with-gradient-outline" target={isExternal && "__blank"} href={url}>{buttonText}</a>
    </div>
  );
}


const Home = () => {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;
  useEffect(() => {
    if (location.hostname.split(".")[0] === "docs") {
      location.replace(siteConfig.url + siteConfig.baseUrl + "/docs")
    }
  }, [])
  return (
    <Layout
      title={`${siteConfig.title}: ${capitalize(siteConfig.tagline)}`}
      description={capitalize(siteConfig.tagline)}
      permaLink="/">
      <header className={clsx('hero hero--secondary', styles.heroBanner)}>
        <div className="container">
          {/* <h1 className="hero__title">{siteConfig.title}</h1> */}
          <img id="home-logo" width="400px" src="/img/full-logo.svg" />
          <h1 style={{ fontWeight: "normal" }}>{capitalize(siteConfig.tagline)}</h1>
          <div className={styles.buttons}>
            <Link
              className="button button--primary button--lg with-gradient"
              to={useBaseUrl('docs/')}>
              Get Started
            </Link>
          </div>
        </div>
      </header>
      <main>
        <section className={styles.features}>
          <h1>Pragma is an open-source language for building GraphQL APIs <i>quickly</i>, and <i>declaratively</i>.</h1>
          <div className={styles.snippet}>
            <img src="/img/snippet.png" width="500px" />
            <div className={styles.snippetDescriptionContainer}>
              <ol>
                <li><div>1</div><h3>Define data models</h3></li>
                <li><div>2</div><h3>Define roles and permissions</h3></li>
                <li><div>3</div><h3>Extend CRUD operations with serverless functions</h3></li>
                <li><div>4</div><h3>Extend permissions with serverless functions</h3></li>
                <li><div>5</div><h3>Start querying your server</h3></li>
              </ol>
            </div>
          </div>
        </section>

        <section className={clsx("container", styles.social)}>
          <h1>Join The Community</h1>
          <div className={styles.socialContainer}>
            <ImageCard isExternal imageUrl="/img/discord.svg" buttonText="Pragmalang Server" url="https://discordapp.com/invite/gbhDnfC" />
            <ImageCard isExternal imageUrl="/img/reddit.svg" buttonText="/r/pragmalang" url="https://www.reddit.com/r/pragmalang/" />
            <ImageCard isExternal imageUrl="/img/twitter.svg" buttonText="@pragmalang" url="https://twitter.com/pragmalang" />
          </div>
        </section>
      </main>
    </Layout>
  );
}

export default Home;
