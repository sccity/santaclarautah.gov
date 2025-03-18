sh '''
    # Build WordPress image
    echo "Building WordPress image..."
    
    # Get commit hash for tag
    IMAGE_NAME="sccity/santaclarautah:${GIT_COMMIT:0:7}-dev"
    
    # Build the image
    echo "Building image: $IMAGE_NAME"
    docker build --platform linux/x86_64 -t $IMAGE_NAME .
    
    # Save image name for other stages
    echo "IMAGE_NAME=$IMAGE_NAME" >> env.properties
    
    echo "Build complete!"
''' 