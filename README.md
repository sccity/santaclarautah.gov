# Santa Clara Utah WordPress Development

This repository contains the WordPress deployment for the Santa Clara Utah website development environment.

## Security Notice

This repository is designed to be public-safe. No sensitive data should ever be committed:

- ❌ Never commit actual secrets or credentials
- ❌ Never commit wordpress-secrets.yaml
- ❌ Never commit .env files
- ❌ Never commit SSL certificates or private keys
- ✅ Use templates for secret files (example: wordpress-secrets.template.yaml)
- ✅ Use Jenkins Credential Store for all secrets
- ✅ Use Kubernetes Secrets for runtime credentials

## Repository Structure
```
.
├── Dockerfile              # WordPress container configuration
├── Jenkinsfile            # CI/CD pipeline definition
├── k8s-manifests/         # Kubernetes configuration files
│   └── *.template.yaml    # Templates for secret files
├── plugins/               # WordPress plugin files (.zip)
└── README.md             # This file
```

## Initial Setup

1. Create necessary Kubernetes secrets:
   ```bash
   # Generate WordPress secrets (DO NOT COMMIT THESE)
   kubectl create secret generic santaclarautah-wordpress-secrets \
     --namespace=webprod \
     --from-literal=WORDPRESS_AUTH_KEY=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_SECURE_AUTH_KEY=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_LOGGED_IN_KEY=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_NONCE_KEY=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_AUTH_SALT=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_SECURE_AUTH_SALT=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_LOGGED_IN_SALT=$(openssl rand -base64 32) \
     --from-literal=WORDPRESS_NONCE_SALT=$(openssl rand -base64 32)
   ```

2. Set up Jenkins credentials:
   - Add `docker-credentials` for Docker registry
   - Add `kube-config` for Kubernetes access

## Development Workflow

1. Create a new branch from `dev`:
   ```bash
   git checkout dev
   git pull
   git checkout -b feature/plugin-update
   ```

2. Add or update plugin files:
   - Place new plugin .zip files in the `plugins/` directory
   - Remove old versions if updating plugins

3. Commit and push changes:
   ```bash
   git add plugins/
   git commit -m "feat: add/update plugins"
   git push origin feature/plugin-update
   ```

4. Create a Pull Request to `dev` branch
   - Jenkins will automatically:
     - Build the Docker image
     - Run tests
     - Verify plugins
     - Deploy to dev environment (on merge)

## Jenkins Pipeline

The Jenkins pipeline includes:
1. Version management (automatic patch increment)
2. Docker image building
3. Plugin verification
4. Deployment to dev environment
5. Health checks

## Required Jenkins Credentials

- `docker-credentials`: Docker registry credentials
- `kube-config`: Kubernetes configuration

## Manual Testing

To test locally before committing:
```bash
# Build image
docker build --platform linux/amd64 -t sccity/santaclarautah:test .

# Test container
docker run --rm sccity/santaclarautah:test ls -la /var/www/html/wp-content/plugins/
```

## Deployment Verification

After deployment, verify:
1. All plugins are present and activated
2. WordPress admin interface is accessible
3. No PHP errors in the logs

## Rollback

To rollback to a previous version:
1. Find the previous version in the deployment history
2. Update the version in `k8s-manifests/wordpress-deployment.yaml`
3. Apply the changes:
   ```bash
   kubectl apply -f k8s-manifests/wordpress-deployment.yaml
   ```

## Security Practices

1. Secrets Management:
   - WordPress secrets are managed via Kubernetes secrets
   - Docker credentials are stored in Jenkins Credentials
   - Kubernetes config is stored in Jenkins Credentials

2. File Security:
   - Use .gitignore to prevent accidental commits of sensitive files
   - Use templates for any files that would contain secrets
   - Never store actual secrets in the repository

3. Access Control:
   - Jenkins uses separate credentials for each service
   - Kubernetes uses namespace isolation
   - Docker registry requires authentication 