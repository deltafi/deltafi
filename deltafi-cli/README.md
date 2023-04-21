# DeltaFi CLI

## Install & Configure

````
git clone git@gitlab.com:deltafi/deltafi.git
cd deltafi/deltafi-cli
sudo ./install.sh
````

Copy the configuration template to `config`:

````
cp config.template config
````

In the `config` file, update the `DELTAFICLI_CHART_PATH` environment variable with the path to the deltafi Helm chart on your system.  By default, it will point at the helmchart in the deltafi monolith project.

To install a standalone system with docker-compose set the `DELTAFI_MODE` to `STANDALONE`  in the `config` file.  Update the `DELTAFICLI_COMPOSE_PATH` environment variable with the path to the deltafi docker-compose directory on your system. By default, it will point at the compose directory in the deltafi monolith project.

## Usage

````
deltafi help
````

_Forked from https://github.com/brotandgames/bagcli._
