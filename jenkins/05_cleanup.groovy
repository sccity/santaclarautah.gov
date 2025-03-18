sh '''
    # Load environment variables from initialize stage
    source env.properties
    
    # Clean up test environment
    echo "Cleaning up test environment..."
    
    # Remove MySQL container
    if [ ! -z "$MYSQL_CONTAINER" ]; then
        echo "Removing MySQL container..."
        docker rm -f $MYSQL_CONTAINER
    fi
    
    # Remove network
    if [ ! -z "$NETWORK_NAME" ]; then
        echo "Removing network..."
        docker network rm $NETWORK_NAME
    fi
    
    echo "Cleanup complete!"
''' 