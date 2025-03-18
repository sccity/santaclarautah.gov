#!/bin/bash

# Exit on error
set -e

# Color definitions for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to check if a command exists
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_message "$RED" "Error: $1 is not installed"
        exit 1
    fi
}

# Function to get current git commit hash
get_commit_hash() {
    git rev-parse --short HEAD
}

# Function to check if we're on the dev branch
check_branch() {
    current_branch=$(git rev-parse --abbrev-ref HEAD)
    if [ "$current_branch" != "dev" ]; then
        print_message "$RED" "Error: Must be on dev branch to deploy"
        print_message "$YELLOW" "Current branch: $current_branch"
        exit 1
    fi
}

# Function to check if there are uncommitted changes
check_changes() {
    if [ -n "$(git status --porcelain)" ]; then
        print_message "$RED" "Error: You have uncommitted changes"
        print_message "$YELLOW" "Please commit or stash your changes before deploying"
        exit 1
    fi
}

# Function to pull latest changes
pull_latest() {
    print_message "$BLUE" "Pulling latest changes from dev branch..."
    git pull origin dev
}

# Function to build the Docker image
build_image() {
    local commit_hash=$1
    print_message "$BLUE" "Building Docker image with tag: $commit_hash"
    docker build --platform linux/x86_64 -t sccity/santaclarautah:$commit_hash-dev .
}

# Function to push the Docker image
push_image() {
    local commit_hash=$1
    print_message "$BLUE" "Pushing Docker image to registry..."
    docker push sccity/santaclarautah:$commit_hash-dev
}

# Function to update the deployment
update_deployment() {
    local commit_hash=$1
    print_message "$BLUE" "Updating deployment to use new image..."
    kubectl set image deployment/wordpress wordpress=sccity/santaclarautah:$commit_hash-dev -n devops
}

# Function to monitor rollout
monitor_rollout() {
    print_message "$BLUE" "Monitoring deployment rollout..."
    kubectl rollout status deployment/wordpress -n devops
}

# Function to verify deployment
verify_deployment() {
    print_message "$BLUE" "Verifying deployment..."
    local pod_name=$(kubectl get pods -n devops -l app=wordpress -o jsonpath="{.items[0].metadata.name}")
    local pod_status=$(kubectl get pod $pod_name -n devops -o jsonpath="{.status.phase}")
    
    if [ "$pod_status" != "Running" ]; then
        print_message "$RED" "Error: Pod is not running. Status: $pod_status"
        print_message "$YELLOW" "Pod logs:"
        kubectl logs $pod_name -n devops
        exit 1
    fi
    
    print_message "$GREEN" "Deployment verified successfully!"
}

# Main deployment process
main() {
    print_message "$BLUE" "Starting dev deployment process..."
    
    # Check prerequisites
    check_command "docker"
    check_command "kubectl"
    check_branch
    check_changes
    
    # Get current commit hash
    commit_hash=$(get_commit_hash)
    print_message "$GREEN" "Current commit: $commit_hash"
    
    # Pull latest changes
    pull_latest
    
    # Build and push Docker image
    build_image $commit_hash
    push_image $commit_hash
    
    # Update deployment
    update_deployment $commit_hash
    
    # Monitor rollout
    monitor_rollout
    
    # Verify deployment
    verify_deployment
    
    print_message "$GREEN" "Deployment completed successfully!"
    print_message "$GREEN" "New version: $commit_hash"
}

# Run main function
main 