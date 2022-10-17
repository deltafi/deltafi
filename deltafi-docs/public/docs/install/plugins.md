# Install Plugins

DeltaFi plugins provide actions and flows for the system. For information on creating plugins, see the [Plugins](/plugins) section.

In this guide we will be installing the Passthrough plugin as an example. Other plugins can be installed using the `deltafi install-plugin` command.

Clone or extract the plugin git repository into your home directory.

```
cd ~
git clone git@gitlab.com:systolic/deltafi/deltafi-passthrough.git
```

Install the plugin using the `deltafi` command.

```
deltafi install-plugin ~/deltafi-passthrough
```

If it succeeds, the output should look something like this:

```
>> 2022-07-08T22:21:21Z deltafi-passthrough: Registering plugin manifest
>> 2022-07-08T22:21:21Z deltafi-passthrough: Installing plugin
Release "deltafi-passthrough" does not exist. Installing it now.
NAME: deltafi-passthrough
LAST DEPLOYED: Fri Jul  8 22:21:21 2022
NAMESPACE: deltafi
STATUS: deployed
REVISION: 1
TEST SUITE: None
>> 2022-07-08T22:21:30Z deltafi-passthrough: Loading plugin flow plans ... waiting for action registration
>> 2022-07-08T22:21:46Z Successfully loaded variables from file variables.json
>> 2022-07-08T22:21:46Z Successfully loaded flow plan decompress-passthrough from file decompress-passthrough-ingress.json
>> 2022-07-08T22:21:47Z Successfully loaded flow plan passthrough from file passthrough-egress.json
>> 2022-07-08T22:21:47Z Successfully loaded flow plan passthrough from file passthrough-ingress.json
>> 2022-07-08T22:21:48Z Successfully loaded flow plan smoke from file smoke-ingress.json
>> 2022-07-08T22:21:48Z Successfully loaded flow plan artificial-enrichment from file binary-enrich.json
>> 2022-07-08T22:21:49Z Successfully loaded flow plan smoke from file smoke-egress.json
>> 2022-07-08T22:21:49Z deltafi-passthrough: Plugin install complete.
```