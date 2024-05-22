/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import React from 'react';
import GraphiQL from 'graphiql';
import 'graphiql/graphiql.min.css';

const Logo = function GraphiQLLogo(props: any) {
  return (
    <div className="title">
      {props.children}
    </div>
  );
};
Logo.displayName = 'GraphiQLLogo';
GraphiQL.Logo = Logo;

const App = () => (
  <GraphiQL
    fetcher={async graphQLParams => {
      const data = await fetch(
        '/api/v2/graphql',
        {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(graphQLParams),
          credentials: 'same-origin',
        },
      );
      return data.json().catch(() => data.text());
    }}
  >
    <Logo><span>DeltaFi Graph<em>i</em>QL</span></Logo>
  </GraphiQL>
);

export default App;
