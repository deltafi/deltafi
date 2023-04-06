### Accessing the DeltaFi development community

1. Register for Slack and GitLab accounts if you don't already have them.

1. Email the DeltaFi team at **deltafi@systolic.com** and request an invitation to the DeltaFi Public workspace on Slack.

1. Email the DeltaFi team at **deltafi@systolic.com** and request access to the DeltaFi group and project repositories.

1. Confirm you have been granted **Developer** access to the required projects in the **DeltaFi** group.  From GitLab, navigate to the DeltaFi group, then select **Menu > Projects > Your projects**

     At a minimum, the following projects should be listed:
    - ansible (deltafi/ansible)
    - deltafi (deltafi/deltafi)
    - devbox (deltafi/devbox)
    - dotfiles (deltafi/dotfiles)
    - deltafi-inator (deltafi/deltafi-inator)

    From the list of projects, you can select any project and view its members. You should see your username listed as a project Developer for all of your required projects.  Note that the DeltaFi projects are maintained under https://gitlab.com/deltafi.


### Setting up a VM based DeltaFi installation

1. Download CentOS Stream 8 x86_64 boot ISO (https://mirror.umd.edu/centos/8-stream/isos/x86_64/CentOS-Stream-8-x86_64-latest-boot.iso).
1. Install VirtualBox:
   1. Create a VM of type Linux with a version of Red Hat (64-bit).
   1. Choose memory size of 8192 MB.
   1. Create a virtual hard disk that is dynamically sized with a limit of at least 50 GB.
   1. After the VM is created, Ctrl+S to choose settings:
       1. Set 4 CPUs under System->Processor tab.
       1. Port forward 2222 to 22 and 8443 to 443 under the Network->Adapter 1->Advanced->Port Forwarding dialog.
       1. Set the CentOS Stream ISO file as the optical drive under Storage->Storage Devices->Controller: IDE. (Click on Empty under Controller: IDE, click the CD icon, select Choose Virtual Optical Disk File..., press the Add button, and select the .iso)

1. Install CentOS:
   1. Start the VM. The installation settings for CentOS should appear.
   1. Set a hostname and turn Ethernet (enp0s3) on under Network & Host Name (NOTE: make sure only one adapter is enabled).
      - Hostname should not be `localhost`. Please set it to `<something>.local.deltafi.org`.
   1. Set the root password under Root Password.
   1. Create a user account under User Creation.
   1. Choose Begin Installation.

1. Start the VM. **Unless otherwise noted, all remaining steps take place in the VM.**

1. Install key in GitLab:
   1. ssh-keygen -t rsa -b 2048 -C "\<email address>"
   1. Paste contents of ~/.ssh/id_rsa.pub into GitLab SSH Keys (under profile preferences).
   1. ssh -T git@gitlab.com (confirm "yes" to permanently add fingerprint to known_hosts)

1. Copy id_rsa.pub to authorized_keys in .ssh directory.

1. Run the devbox install script:
    1. Create ~/.vault-password file containing password (ask developer).
    1. sudo yum install epel-release
    1. git archive --remote=git@gitlab.com:deltafi/devbox HEAD install.sh | tar -x --to-stdout | sh
    1. Add the following to ~/.gitconfig.local:
    ```
    [user]
        name = <FIRST LAST>
        email = <YOUR EMAIL ADDRESS>

1. Configure gradle:
    1. Create a personal access token in GitLab (can select just api).
    1. Create ~/.gradle/gradle.properties with the following:
    ```
    gitLabTokenType=Private-Token
    gitLabToken=<PERSONAL ACCESS TOKEN>

1. Add the following localhost aliases to /etc/hosts (or C:\Windows\System32\drivers\etc\hosts):
    ```
    metrics.local.deltafi.org nifi.local.deltafi.org

1. Install DeltaFi:
    1. git clone git@gitlab.com:deltafi/deltafi.git
    1. cd deltafi/deltafi-cli
    1. ./install.sh
    1. deltafi install --timeout 10m
