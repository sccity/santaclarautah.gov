sh '''
    # Test WordPress installation
    echo "Testing WordPress installation..."
    
    # Get the image name from the build
    IMAGE_NAME="sccity/santaclarautah:${COMMIT_HASH}-dev"
    
    # Create a temporary container for testing
    CONTAINER_ID=$(docker create --rm -p 8080:8080 $IMAGE_NAME)
    docker start $CONTAINER_ID
    
    # Wait for Apache to start
    echo "Waiting for Apache to start..."
    sleep 5
    
    # Test HTTP response
    echo "Testing HTTP response..."
    HTTP_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080)
    
    if [ "$HTTP_RESPONSE" != "200" ]; then
        echo "Error: WordPress returned HTTP $HTTP_RESPONSE"
        echo "Container logs:"
        docker logs $CONTAINER_ID
        docker rm -f $CONTAINER_ID
        exit 1
    fi
    
    echo "WordPress installation is accessible (HTTP 200)"
    
    # Clean up
    docker rm -f $CONTAINER_ID
    
    echo "All tests passed successfully!"
'''