backend-jira-versions
=====================

Jenkins backend application to add core and plugin versions to JIRA

This application will parse the Maven repository information to get the release version and release date for Jenkins core and Jenkins plugins. 

Core versions will be added as jenkins-<VERSION> (e.g., jenkins-1.555) , plugins will be added as <ARTIFACTID>-<VERSION> (e.g., email-ext-2.37).
