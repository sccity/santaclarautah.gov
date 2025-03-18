THIS IS OUTDATED AND NO LONGER VALID!!!!!!!!
KEPT FOR POSTERITY
#!/bin/bash

set -e  # Exit on error

# Apply PVCs first
echo "Creating PVCs..."
kubectl apply -f k8s-manifests/wordpress-core-pvc.yaml
kubectl apply -f k8s-manifests/wordpress-content-pvc.yaml

# Initialize core volume
echo "Initializing core volume..."
kubectl apply -f k8s-manifests/init-core-pod.yaml

# Wait for pod to start
echo "Waiting for init pod to start..."
kubectl wait --for=condition=Ready pod/santaclarautah-dev-init -n webprod --timeout=30s || true

# Check pod status and logs
echo "Checking init pod progress..."
kubectl get pod santaclarautah-dev-init -n webprod
echo "Pod logs:"
kubectl logs -f santaclarautah-dev-init -n webprod

# Verify pod succeeded
POD_STATUS=$(kubectl get pod santaclarautah-dev-init -n webprod -o jsonpath='{.status.phase}')
if [ "$POD_STATUS" != "Succeeded" ]; then
    echo "Init pod failed with status: $POD_STATUS"
    kubectl describe pod santaclarautah-dev-init -n webprod
    echo "Cleaning up failed pod..."
    kubectl delete pod santaclarautah-dev-init -n webprod --force --grace-period=0
    exit 1
fi

# Clean up init pod
echo "Cleaning up initialization pod..."
kubectl delete -f k8s-manifests/init-core-pod.yaml

# Apply the main deployment
echo "Deploying WordPress..."
kubectl apply -f k8s-manifests/wordpress-deployment.yaml

echo "Setup complete! WordPress should be starting up..." 