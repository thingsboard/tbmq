## Run

### Without clonning repo
If charts are hosted, they could be available on this [repo](https://laarchenko.github.io/), so you can use them with these commands:
```
helm repo add tbRepo https://laarchenko.github.io/
helm repo update
```
to add helm charts and run them this way:

1. to run full tb infrastructure -> 
```
helm install --debug --create-namespace --namespace tb tbRepo/tboard-complex --generate-name
```
2. to run only broker ->
```
helm install --namespace="NAMESPACE WHERE POSTGRES AND KAFKA DEPLOYED" --generate-name -f defaultValues.yaml tbRepo/tboard-standalone
```
 
### If you ready to clone repo
If you would like to run thingsboard from zero, use the following command:
```
helm install --create-namespace --namespace=tb --generate-name tboard-complex/
```
In case you need just broker, execute the following steps:
1. Create file **defaultValues.yaml** and fill in it by the structure:
```
kafka:
  serversUrl: "KAFKA`S SERVICE URL"
postgresql:
  springDataSourceUrl: "DB CONNECTION URL FOR SPRING"
  springDataSourceUsername: "POSTGRES USERNAME"
  springDataSourcePassword: "POSTGRES PASSWORD"
```
2. Install broker with the following command:
```
helm install --namespace="NAMESPACE WHERE POSTGRES AND KAFKA DEPLOYED" --generate-name -f defaultValues.yaml tboard-standalone/ 
```

### Troubleshooting

It may be that due to slow network, some images need more time to be donwloaded. This can cause
```
Failed to pull image "someImage": rpc error: code = Unknown desc = context deadline exceeded
```
error. To avoid it, pull image by yourself
```
docker pull IMAGE_NAME
```
and bugged pod will be restarted. Or no to wait, you can delete and restart by yourself. 
