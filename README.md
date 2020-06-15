# Simple Fi

## Development

### Server

Start a REPL, load the `simplefi.server` namespace, and evaluate `(run-dev)`. This will start a development server with development interceptors for showing errors, etc.

### UI

Start the development server as noted above, then in a new terminal, run `shadow-cljs watch app`, which builds the CLJS into a JS file that is linked in the app HTML, served by the server. Changes to cljs files will cause a rebuild of the JS.

## Deployment

Run the `deploy.sh` script, which automatically builds, tags, and pushes the docker image to the online registry, logs into the remote production server, pulls the latest version of the image, then runs it.
