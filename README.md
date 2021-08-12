# SignPath Plugin

## Introduction

The SignPath Plugin for Jenkins allows you to integrate code signing with [SignPath](https://about.signpath.io) in your Jenkins pipeline.

## How to use this plugin

### Prerequisites

The following plugins must be installed:

- Credentials Binding [com.cloudbees.plugins.credentials]
- Git [hudson.plugins.git.util.Build]
- Pipeline

Also, **PowerShell** (Version 5.1 or greater) must be installed on the Jenkins master node and the [SignPath PowerShell](https://www.powershellgallery.com/packages/SignPath) module must be installed and available to the user running the Jenkins server.

Make sure that the correct **Jenkins URL** is set unter _Manage Jenkins / Configure system._

### Configuration

On SignPath.io:

1. Add a Trusted Build System on SignPath and copy the generated **Trusted Build System Token**
2. Link the Trusted Build System to all projects that are build with it
3. Add one or more CI users (e.g. one per team) and copy the generated **CI User Token** (API Token)

On Jenkins:

1. Store the **Trusted Build System Token** in a System Credential (Under Manage Jenkins / Manage Credentials)
2. Store the CI User Token(s) in a System Credential so that it is available to the build pipelines of the respective projects

_Note: Currently, the SignPath plugin requires you to use **git** as your source control system. The git repository origin information is extracted and included in the signing request._

### Usage

In your `Jenkinsfile`, make sure the artifacts to be signed are pushed to the master node by adding a stage e.g.

```
stage('Archive') {
  steps {
    archiveArtifacts artifacts: "build-output/**", fingerprint: true
  }
}
```

Include the `submitSigningRequest` and optionally, the `getSignedArtifact` steps in your build pipeline. The artifacts to be signed need to be uploaded to the Jenkins master by calling the `archiveArtifacts` step.

#### Example: Submit a synchronous signing request

```
stage('Sign with SignPath') {
  steps {
    submitSigningRequest( 
      ciUserTokenCredentialId: "${CI_USER_TOKEN_CREDENTIAL_ID}", 
      trustedBuildSystemTokenCredentialId: "${TRUSTED_BUILD_SYSTEM_TOKEN_CREDENTIAL_ID}", 
      organizationId: "${ORGANIZATION_ID}",
      projectSlug: "${PROJECT_SLUG}",
      signingPolicySlug: "${SIGNING_POLICY_SLUG}",
      inputArtifactPath: "build-output/my-artifact.exe",
      outputArtifactPath: "build-output/my-artifact.signed.exe",
      waitForCompletion: true
    )
  }
}
```

#### Example: Submit an asynchronous signing request

```
stage('Sign with SignPath') {
  steps {
    script {
      signingRequestId = submitSigningRequest( 
        ciUserTokenCredentialId: "${CI_USER_TOKEN_CREDENTIAL_ID}", 
        trustedBuildSystemTokenCredentialId: "${TRUSTED_BUILD_SYSTEM_TOKEN_CREDENTIAL_ID}",
        organizationId: "${ORGANIZATION_ID}",
        projectSlug: "${PROJECT_SLUG}",
        signingPolicySlug: "${SIGNING_POLICY_SLUG}",
        inputArtifactPath: "build-output/my-artifact.exe",
        outputArtifactPath: "build-output/my-artifact.signed.exe",
        waitForCompletion: false
      )
    }
  }
}
stage('Download Signed Artifact') {
  input {
    id "WaitForSigningRequestCompleted"
    message "Has the signing request completed?"
  }
  steps{
    getSignedArtifact( 
      ciUserToken: "${CI_USER_TOKEN}", 
      organizationId: "${ORGANIZATION_ID}",
      signingRequestId: "${signingRequestId}",
      outputArtifactPath: "build-output/my-artifact.exe"
    )
  }
}

```

#### Parameters

| Parameter                                             |      |
| ----------------------------------------------------- | ---- |
| `apiUrl`                                              | (optional) The API endpoint of SignPath. Defaults to `https://app.signpath.io/api`
| `ciUserTokenCredentialId`                             | The ID of the credential containing the **CI User Token**
| `trustedBuildSytemTokenCredentialId`                  | The ID of the credential containing the **Trusted Build System Token**
| `organizationId`, `projectSlug`, `signingPolicySlug`  | Specify which organization, project and signing policy to use for signing. See the [official documentation](https://about.signpath.io/documentation/build-system-integration)
| `inputArtifactPath`                                   | The relative path of the artifact to be signed
| `outputArtifactPath`                                  | The relative path where the signed artifact is stored after signing
| `waitForCompletion`                                   | set to `true` for synchronous and `false` for asynchronous signing requests

## Support

The plugin is compatible with Jenkins 2.277.1 or higher.

Please refer to the support available in your respective [SignPath edition](https://about.signpath.io/product/editions).

## License

Copyright by SignPath GmbH

The SignPath Jenkins Plugin is being developed by [SignPath](https://about.signpath.io) and licensed under the **GNU General Public License v3 (GPL-3)**
