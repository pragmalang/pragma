import React from "react"
import GraphiQL from 'graphiql';
import Head from '@docusaurus/Head';

function graphQLFetcher(graphQLParams) {
  return fetch(window.location.origin + '/graphql', {
    method: 'post',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(graphQLParams),
  }).then(response => response.json());
}

export default props =>
  <div style={{ height: 400 }}>
    <Head>
      <link href="/graphiql.min.css" rel="stylesheet" />
      <link href="/playground.css" rel="stylesheet" />
    </Head>
    <GraphiQL fetcher={graphQLFetcher} defaultQuery={props.defaultQuery || "# Type your query here\n"} />
  </div>