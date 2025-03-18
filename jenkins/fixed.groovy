def call(String buildUrl) {
    emailext (
        subject: "✅ WordPress Build Fixed",
        body: """
            Build Status: Fixed
            Build URL: ${buildUrl}
            
            Changes in this build:
            ${currentBuild.changeSets}
        """,
        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
        to: 'rlevsey@santaclarautah.gov, lhaynie@santaclarautah.gov'
    )
} 