import React, { useEffect, useState } from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';
import { capitalize } from "../utils";
import MailchimpSubscribe from "react-mailchimp-subscribe";

const EmailForm = ({ subscribe, status, message }) => {
  const [email, setEmail] = React.useState(null)
  return (
    <form onSubmit={e => {
      e.preventDefault()
      email && subscribe(email)
    }}>
      <input className="input" type="email" onChange={e => setEmail(e.target.value)} />
      <button className={`button button--primary ${styles.emailSubmitButton}`} type="submit">Submit</button>
      <div style={{ color: "red" }}>{status}: {message}</div>
    </form>
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
          {/* <h1 className="hero__title">{siteConfig.title}</h1> */}
          <img id="home-logo" width="400px" src="/img/full-logo.svg" />
          <p className="hero__subtitle">{capitalize(siteConfig.tagline)}</p>
          <div className={styles.buttons}>
            <Link
              className="button button--primary button--lg"
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
              <li><div>1</div><h3>Define Data Models</h3></li>
              <li><div>2</div><h3>Define Roles And Permission</h3></li>
              <li><div>3</div><h3>Extend CRUD Operations With Serverless Functions</h3></li>
              <li><div>4</div><h3>Extend Permisions With Serverless Functions</h3></li>
            </ol>
          </div>
        </section>
      </main>
    </Layout>
  );
}

export default Home;
