### Install / Upgrade

    helm dependency update
    helm upgrade deltafi . -n deltafi --create-namespace --install

### Uninstall

    helm uninstall deltafi -n deltafi
