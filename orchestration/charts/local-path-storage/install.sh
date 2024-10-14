mkdir -p /data/deltafi/local
chmod a+rwx /data/deltafi/local
kubectl apply -f local-path-storage.yaml
kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
