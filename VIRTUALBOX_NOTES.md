Nifty things to know when setting up or using your Oracle VirtualBox



**Changing Removable Media**
You can remove/add media while your VM is running (yes, it's true)  The Settings dialog is disabled while the VM is in the Running or Saved state. But you can access the add/remove functions under the **Devices** menu. It's not necessary to shut down and restart the VM every time you want to change media.
You can also use the Devices menu to:
attach the host drive, floppy or DVD image to the guest, as described in Section 3.7, “Storage Settings” of the Oracle VirtualBox Users Manual.
Create a virtual ISO (VISO) from selected files on the host.

**SSH public key doesn't validate in GitLab when executing ssh -T git@gitlab.com**
TODO some complaint about connect or device doesn't exist
Check your VM's network settings.  If you have more than one adapter enabled, it may be the cause of this problem.

References:
https://www.virtualbox.org/manual/
