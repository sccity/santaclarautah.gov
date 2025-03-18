#!/bin/bash

# Check if a commit message was provided
if [ $# -eq 0 ]; then
    echo "Error: Please provide a commit message"
    echo "Usage: ./update-dev.sh \"Your commit message\""
    exit 1
fi


# Store the commit message
COMMIT_MESSAGE="$1"

# Function to check command status
check_status() {
    if [ $? -ne 0 ]; then
        echo "Error: $1"
        exit 1
    fi
}

# Ensure we're on dev branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "dev" ]; then
    echo "Switching to dev branch..."
    git checkout dev
    check_status "Failed to switch to dev branch"
fi

# Stash any local changes
echo "Stashing local changes..."
git stash
# Don't check status as it will fail if there are no changes

# Pull latest changes
echo "Pulling latest changes from dev branch..."
git pull origin dev
check_status "Failed to pull latest changes"

# Pop stashed changes
echo "Restoring local changes..."
git stash pop 2>/dev/null || true
# Don't check status as it will fail if there were no stashed changes

# Add all changes
echo "Adding all changes..."
git add .
check_status "Failed to add changes"

# Check if there are changes to commit
if ! git diff --cached --quiet; then
    # Changes exist, commit them
    echo "Committing changes..."
    git commit -m "$COMMIT_MESSAGE"
    check_status "Failed to commit changes"

    # Push to dev branch
    echo "Pushing to dev branch..."
    git push origin dev
    check_status "Failed to push changes"

    echo "Success! All changes have been pushed to dev branch"
else
    echo "No changes to commit. Working tree is clean."
    exit 0
fi 