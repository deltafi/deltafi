# demo

Downloads of this project should be limited to the test branch for stability.
This project was created as a merge of a GitLab template project (Node/Express) and a new
Vue.js project.  
This project has been developed using VSCode running on GitBash (GitForWindows) and was created using the following command:
$ winpty vue.cmd create deltafi-ui

An initial UI view to display Nodes and Pods is currently displayed.  Routing is implemented, as demonstrated by the presence of an About page.

vue.conf defines the project configuration which includes the following plugins:
[Vue 3] babel
typescript
pwa
router
vuex

## Version Information
$ node -v
v16.11.0
$ npm -v
8.0.0
$ 
vue CLI v4.5.13


## Project setup - local machine
```
The only option available at this time for running the UI is to:
Download/install the source code 
install Node.js and npm
install Vue.js
Run the app using NPM

Follow the steps below.

1. Install the latest LTS verson of Node.js and NPM on your machine
$ npm install -g @vue/cli
2. Navigate to https://gitlab.com/systolic/deltafi/deltafi-ui  and download zip archive of the test branch to your workspace directory on your linux machine


# RUNNING THE UI
```
### Option 1: Compile, run and access UI on localhost
Run the app using the Vue CLI service.
$ npm run serve-app
The application will be served at browser URL http://localhost:8080 or other localhost port as indicated

```
### Option 2: Use Vue Dashboard interface to import and run UI
$ vue ui
The Vue interface will be displayed at http://localhost:8000.  Navigate to the Vue Project Manager.  Select "Import" and import the deltafi-ui project to the dashboard
Click on the deltafi-ui project.  The Vue Project Manager automatically starts the localhost host and the appliation will be served via browser URL http://localhost:8000


```

# DEVELOPERS
### Run localhost JSON server generating fake data for UI (not yet implemented)
npm serve-json
Localhost JSON server will be served at URL http://localhost:3000

### Compiles and hot-reloads for development
```
npm run serve-app
```

### Compiles and minifies for production
```
npm run build
```

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).
