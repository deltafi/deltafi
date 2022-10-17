# DeltaFi Documentation

The DeltaFi docs are built with [Docute](https://docute.egoist.sh/).

Markdown files are located in `public/docs`.

## Development

To run the documentation server locally, run:

```
npm install
npm run serve
```

This will start a server on http://localhost:8080/. Changes you make to the Markdown files will be live-updated.

## Build

To build a static version of the site, run:

```
npm install
npm run build
```

This will produce a static, compressed, and offline version of the docs that is ready for deployment. Files will be be placed in the `dist` directory.
