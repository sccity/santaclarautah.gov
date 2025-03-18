def call(String buildUrl) {
    emailext (
        subject: "❌ WordPress Build Failed",
        body: """
            Build Status: Failed
            Build URL: ${buildUrl}
            
            Changes in this build:
            ${currentBuild.changeSets}
            
            Console Output:
            ${currentBuild.getLog()}
        """,
        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
        attachmentsPattern: '**/build.log',
        to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov'
    )
} 