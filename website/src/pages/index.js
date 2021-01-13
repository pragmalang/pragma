import React, { useEffect, useState } from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';
import { capitalize } from "../utils";

function ImageCard({ altText, imageUrl, url, isExternal }) {
  const imgUrl = useBaseUrl(imageUrl)
  return (
    <div className={clsx('col col--4', styles.imageCard)}>
      <div className="text--center">
        <a href={url} target={isExternal && "__blank"}>
          <img src={imgUrl} className={styles.imageCardImage} alt={altText} />
        </a>
      </div>
    </div>
  );
}

function ContentCard({ header, content }) {
  return (
    <div className={clsx("card", styles.card)}>
      <div className="card__header">
        <h3 className={clsx(styles.cardTitle)}>{header}</h3>
      </div>
      <div className="card__body">
        {content}
      </div>
    </div>
  )
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
          <h1>
            Pragma is an easy to learn, open-source language for building
            GraphQL APIs <i>quickly</i>, and <i>declaratively</i>.
          </h1>
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

        <section className={clsx("container", styles.gqlIntro)}>
          <h1>Never Used GraphQL? Fear Not!</h1>
          <p style={{ fontWeight: "300" }}>
            Getting started with Pragma does not require any prior knowledge of GraphQL
          </p>
          <Link
            className="button button--primary button--outline button--lg with-gradient-outline"
            to={useBaseUrl('docs/getting-started/graphql-intro')}>
            Let Pragma Be The Beginning
          </Link>
        </section>

        <section className={clsx("container", styles.audience)}>
          <h1>Who Is Pragma For?</h1>
          <div className={clsx(styles.cardContainer)}>
            <ContentCard
              header="Startups"
              content={
                <p>
                  Pragma is a perfect fit for startups. In fact, the original
                  motivation behind it was testing new ideas in hours or days
                  instead of weeks or months. It is designed to <strong>help you move quickly.</strong>
                </p>
              }
            />
            <ContentCard
              header="Indie Hackers"
              content={
                <p>
                  Pragma allows you to go from idea to implementation <strong>in minutes</strong>.
                  <br />
                  <strong>Focus on user-facing logic</strong>,
                  and let Pragma do the server-side magic.
                  Don't worry about having to write networking logic, nor designing the auth layer.
                </p>
              }
            />
            <ContentCard
              header="Developers"
              content={
                <p>
                  If you're a front-end developer, or you just don't want to
                  deal with the toil of writing the same back-end logic for the thousandth time,
                  Pragma is for you.
                  Don't worry about resolvers, migrations, or authentication.
                </p>
              }
            />
          </div>

          <div style={{ width: "100%", display: "flex", justifyContent: "center" }}>
            <Link
              className={clsx("button button--primary button--lg with-gradient", styles.audienceGetStartedButton)}
              to={useBaseUrl('docs/install')}>
              Try It Out!
          </Link>
          </div>
        </section>

        <section style={{ textAlign: "center" }} className={clsx("container", styles.social)}>
          <h1>Connect With Us</h1>
          <p style={{ fontWeight: "300" }}>If you have any questions, opinions, or remarks, we'd love to talk!</p>
          <div className={styles.socialContainer}>
            <ImageCard isExternal imageUrl="/img/discord.svg" altText="Pragmalang Server" url="https://discordapp.com/invite/gbhDnfC" />
            <ImageCard isExternal imageUrl="/img/reddit.svg" altText="/r/pragmalang" url="https://www.reddit.com/r/pragmalang/" />
            <ImageCard isExternal imageUrl="/img/twitter.svg" altText="@pragmalang" url="https://twitter.com/pragmalang" />
            <ImageCard isExternal imageUrl="/img/linkedin.svg" altText="@pragma" url="https://www.linkedin.com/company/pragmalang" />
          </div>
        </section>
      </main>
    </Layout >
  );
}

export default Home;
