1. Register for Slack and GitLab accounts if you don't already have them.

2. Email the DeltaFi team at **deltafi@systolic.com** and request an invitation to the DeltaFi Public workspace on Slack.

3. Email the DeltaFi team at **deltafi@systolic.com** and request access to the DeltaFi group and project repositories.

4. Confirm you have been granted **Developer** access to the required projects in the **DeltaFi** group.  From GitLab, navigate to the DeltaFi group, then select **Menu > Projects > Your projects**

     At a minimum, the following projects should be listed:
    - ansible (SYSTOLIC/DeltaFi/ansible)
    - deltafi (SYSTOLIC/DeltaFi/deltafi)
    - devbox (SYSTOLIC/DeltaFi/devbox)
    - dotfiles (SYSTOLIC/DeltaFi/dotfiles)
    - deltafi-inator (SYSTOLIC/DeltaFi/deltafi-inator)

    From the list of projects, you can select any project and view its members. You should see your username listed as a project Developer for all of your required projects.  Note that the DeltaFi projects are maintained under https://gitlab.com/systolic/deltafi.

5. Download CentOS Stream 8 x86_64 boot ISO (https://mirror.umd.edu/centos/8-stream/isos/x86_64/CentOS-Stream-8-x86_64-latest-boot.iso).
6. Install VirtualBox:
   1. Create a VM of type Linux with a version of Red Hat (64-bit).
   2. Choose memory size of 8192 MB.
   3. Create a virtual hard disk that is dynamically sized with a limit of at least 50 GB.
   4. After the VM is created, Ctrl+S to choose settings:
       1. Set 4 CPUs under System->Processor tab.
       2. Port forward 2222 to 22 and 8443 to 443 under the Network->Adapter 1->Advanced->Port Forwarding dialog.
       3. Set the CentOS Stream ISO file as the optical drive under Storage->Storage Devices->Controller: IDE. (Click on Empty under Controller: IDE, click the CD icon, select Choose Virtual Optical Disk File..., press the Add button, and select the .iso)

7. Install CentOS:
   1. Start the VM. The installation settings for CentOS should appear.
   2. Set a hostname and turn Ethernet (enp0s3) on under Network & Host Name (NOTE: make sure only one adapter is enabled).
      - Hostname should not be `localhost`. Please set it to `<something>.local.deltafi.org`.
   3. Set the root password under Root Password.
   4. Create a user account under User Creation.
   5. Choose Begin Installation.

8. Start the VM. **Unless otherwise noted, all remaining steps take place in the VM.**

9. Install key in GitLab:
   1. ssh-keygen -t rsa -b 2048 -C "\<email address>"
   2. Paste contents of ~/.ssh/id_rsa.pub into GitLab SSH Keys (under profile preferences).
   3. ssh -T git@gitlab.com (confirm "yes" to permanently add fingerprint to known_hosts)

10. Copy id_rsa.pub to authorized_keys in .ssh directory.

11. Run the devbox install script:
    1. Create ~/.vault-password file containing password (ask developer).
    2. sudo yum install epel-release
    3. git archive --remote=git@gitlab.com:systolic/deltafi/devbox HEAD install.sh | tar -x --to-stdout | sh
    4. Add the following to ~/.gitconfig.local:
    ```
    [user]
        name = <FIRST LAST>
        email = <YOUR EMAIL ADDRESS>

12. Configure gradle:
    1. Create a personal access token in GitLab (can select just api).
    2. Create ~/.gradle/gradle.properties with the following:
    ```
    gitLabTokenType=Private-Token
    gitLabToken=<PERSONAL ACCESS TOKEN>

13. Add the following localhost aliases to /etc/hosts (or C:\Windows\System32\drivers\etc\hosts):
    ```
    grafana.local.deltafi.org minio.local.deltafi.org nifi.local.deltafi.org

14. Install DeltaFi:
    1. git clone git@gitlab.com:systolic/deltafi/deltafi.git
    2. cd deltafi/deltafi-cli
    3. ./install.sh
    4. deltafi install --timeout 10m
