# Auth Template

This is a template for a project that uses session-based authentication. It's by no means perfect but can provide a good starting point for a web app with authentication and a pedestal backend. I figure this repo can serve as a reference for myself later, and maybe others will find it useful too.

The database layer uses postgresql, but this can be switched out if desired.

Feel free to copy and use this template however you want.

## Tooling

* pedestal for the backend service
* java-time for handling time and dates
* postal for sending emails
* yogthos/config for configuration
* next.jdbc for database interaction
* hiccup for rendering html
* buddy-hashers for hashing passwords and checking raw passwords against stored hashes
* hikaricp for database connection pooling
* docker for packaging and deployment
* shadow-cljs for clojurescript compilation

## Auth Flow

![auth flow](auth-flow.png)

## Data Model

![ER diagram](er-diagram.png)

## Development

### Configuration

Create a `dev-config.edn` file and fill out the required config values. See the `auth-template.config` namespace and the `dev-config-example.edn` file for more details.

### Server

Start a REPL, load the `auth-template.server` namespace, and evaluate `(run-dev)`. This will start a development server with development interceptors for showing errors, etc.

### UI

Start the development server as noted above, then in a new terminal, run `shadow-cljs watch app`, which builds the CLJS into a JS file that is linked in the app HTML, which is served by the server. Changes to cljs files will cause a rebuild of the JS.

## Deployment

Run the `deploy.sh` script (needs to be customized), which automatically builds, tags, and pushes the docker image to the online registry, logs into the remote production server, pulls the latest version of the image, then runs it.
