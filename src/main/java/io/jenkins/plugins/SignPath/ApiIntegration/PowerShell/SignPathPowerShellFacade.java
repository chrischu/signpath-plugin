package io.jenkins.plugins.SignPath.ApiIntegration.PowerShell;

import io.jenkins.plugins.SignPath.ApiIntegration.ApiConfiguration;
import io.jenkins.plugins.SignPath.ApiIntegration.Model.RepositoryMetadataModel;
import io.jenkins.plugins.SignPath.ApiIntegration.Model.SigningRequestModel;
import io.jenkins.plugins.SignPath.ApiIntegration.Model.SigningRequestOriginModel;
import io.jenkins.plugins.SignPath.ApiIntegration.SignPathCredentials;
import io.jenkins.plugins.SignPath.ApiIntegration.SignPathFacade;
import io.jenkins.plugins.SignPath.Common.TemporaryFile;
import io.jenkins.plugins.SignPath.Exceptions.SignPathFacadeCallException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of the
 *
 * @see SignPathFacade interface
 * that delegates to the local SignPath PowerShell Module
 * A version of the Module must be installed in the local powershell / powershell core
 */
public class SignPathPowerShellFacade implements SignPathFacade {

    private final PowerShellExecutor powerShellExecutor;
    private final SignPathCredentials credentials;
    private final ApiConfiguration apiConfiguration;
    private final PrintStream logger;

    public SignPathPowerShellFacade(PowerShellExecutor powerShellExecutor, SignPathCredentials credentials, ApiConfiguration apiConfiguration, PrintStream logger) {
        this.powerShellExecutor = powerShellExecutor;
        this.credentials = credentials;
        this.apiConfiguration = apiConfiguration;
        this.logger = logger;
    }

    @Override
    public TemporaryFile submitSigningRequest(SigningRequestModel submitModel) throws IOException, SignPathFacadeCallException {
        TemporaryFile outputArtifact = new TemporaryFile();
        PowerShellCommand submitSigningRequestCommand = createSubmitSigningRequestCommand(submitModel, outputArtifact);
        executePowerShellSafe(submitSigningRequestCommand);
        return outputArtifact;
    }

    @Override
    public UUID submitSigningRequestAsync(SigningRequestModel submitModel) throws SignPathFacadeCallException {
        PowerShellCommand submitSigningRequestCommand = createSubmitSigningRequestCommand(submitModel, null);
        String result = executePowerShellSafe(submitSigningRequestCommand);
        return extractSigningRequestId(result);
    }

    @Override
    public TemporaryFile getSignedArtifact(UUID organizationId, UUID signingRequestID) throws IOException, SignPathFacadeCallException {
        TemporaryFile outputArtifact = new TemporaryFile();
        PowerShellCommand getSignedArtifactCommand = createGetSignedArtifactCommand(organizationId, signingRequestID, outputArtifact);
        executePowerShellSafe(getSignedArtifactCommand);
        return outputArtifact;
    }

    private String executePowerShellSafe(PowerShellCommand command) throws SignPathFacadeCallException {
        PowerShellExecutionResult result = powerShellExecutor.execute(command, apiConfiguration.getWaitForPowerShellTimeoutInSeconds());

        if (result.getHasError())
            throw new SignPathFacadeCallException(String.format("PowerShell script exited with error: '%s'", result.getErrorDescription()));

        logger.println("PowerShell script ran successfully.");
        return result.getOutput();
    }

    private UUID extractSigningRequestId(String output) throws SignPathFacadeCallException {
        // Last output line = return value => we want the PowerShell script to return a GUID
        final String guidRegex = "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$";
        Matcher regexResult = Pattern.compile(guidRegex, Pattern.MULTILINE).matcher(output);
        if (!regexResult.find()) {
            throw new SignPathFacadeCallException("Unexpected output from PowerShell, did not find a valid signingRequestId.");
        }
        String signingRequestId = regexResult.group(0);
        return UUID.fromString(signingRequestId);
    }

    private PowerShellCommand createSubmitSigningRequestCommand(SigningRequestModel signingRequestModel, TemporaryFile outputArtifact) {
        PowerShellCommandBuilder commandBuilder = new PowerShellCommandBuilder("Submit-SigningRequest");
        commandBuilder.appendParameter("ApiUrl", apiConfiguration.getApiUrl().toString());
        commandBuilder.appendParameter("CIUserToken", credentials.toString());
        commandBuilder.appendParameter("OrganizationId", signingRequestModel.getOrganizationId().toString());
        commandBuilder.appendParameter("InputArtifactPath", signingRequestModel.getArtifact().getAbsolutePath());
        commandBuilder.appendParameter("ProjectSlug", signingRequestModel.getProjectSlug());
        commandBuilder.appendParameter("SigningPolicySlug", signingRequestModel.getSigningPolicySlug());

        if (signingRequestModel.getArtifactConfigurationSlug() != null)
            commandBuilder.appendParameter("ArtifactConfigurationSlug", signingRequestModel.getArtifactConfigurationSlug());

        if (signingRequestModel.getDescription() != null)
            commandBuilder.appendParameter("Description", signingRequestModel.getDescription());

        commandBuilder.appendParameter("ServiceUnavailableTimeoutInSeconds", String.valueOf(apiConfiguration.getServiceUnavailableTimeoutInSeconds()));
        commandBuilder.appendParameter("UploadAndDownloadRequestTimeoutInSeconds", String.valueOf(apiConfiguration.getUploadAndDownloadRequestTimeoutInSeconds()));

        SigningRequestOriginModel origin = signingRequestModel.getOrigin();
        RepositoryMetadataModel repositoryMetadata = origin.getRepositoryMetadata();

        commandBuilder.appendCustom("-Origin @{'BuildData' = @{" +
                        "'Url' = \"$($env:BuildUrl)\";" +
                        "'BuildSettingsFile' = \"$($env:BuildSettingsFile)\";" +
                        "};" +
                        "'RepositoryData' = @{" +
                        "'BranchName' = \"$($env:BranchName)\";" +
                        "'CommitId' = \"$($env:CommitId)\";" +
                        "'Url' = \"$($env:RepositoryUrl)\";" +
                        "'SourceControlManagementType' = \"$($env:SourceControlManagementType)\"" +
                        "}}",
                new EnvironmentVariable("BuildUrl", origin.getBuildUrl()),
                new EnvironmentVariable("BuildSettingsFile", String.format("@%s", origin.getBuildSettingsFile().getAbsolutePath())),
                new EnvironmentVariable("BranchName", repositoryMetadata.getBranchName()),
                new EnvironmentVariable("CommitId", repositoryMetadata.getCommitId()),
                new EnvironmentVariable("RepositoryUrl", repositoryMetadata.getRepositoryUrl()),
                new EnvironmentVariable("SourceControlManagementType", repositoryMetadata.getSourceControlManagementType()));

        if (outputArtifact != null) {
            commandBuilder.appendFlag("WaitForCompletion");
            commandBuilder.appendParameter("OutputArtifactPath", outputArtifact.getAbsolutePath());
            commandBuilder.appendParameter("WaitForCompletionTimeoutInSeconds", String.valueOf(apiConfiguration.getWaitForCompletionTimeoutInSeconds()));
            commandBuilder.appendFlag("Force");
        }

        commandBuilder.appendFlag("Verbose");
        return commandBuilder.build();
    }

    private PowerShellCommand createGetSignedArtifactCommand(UUID organizationId, UUID signingRequestId, TemporaryFile outputArtifact) {
        PowerShellCommandBuilder commandBuilder = new PowerShellCommandBuilder("Get-SignedArtifact");
        commandBuilder.appendParameter("ApiUrl", apiConfiguration.getApiUrl().toString());
        commandBuilder.appendParameter("CIUserToken", credentials.toString());
        commandBuilder.appendParameter("OrganizationId", organizationId.toString());
        commandBuilder.appendParameter("SigningRequestId", signingRequestId.toString());
        commandBuilder.appendParameter("ServiceUnavailableTimeoutInSeconds", String.valueOf(apiConfiguration.getServiceUnavailableTimeoutInSeconds()));
        commandBuilder.appendParameter("UploadAndDownloadRequestTimeoutInSeconds", String.valueOf(apiConfiguration.getUploadAndDownloadRequestTimeoutInSeconds()));
        commandBuilder.appendParameter("OutputArtifactPath",  outputArtifact.getAbsolutePath());
        commandBuilder.appendParameter("WaitForCompletionTimeoutInSeconds", String.valueOf(apiConfiguration.getWaitForCompletionTimeoutInSeconds()));
        commandBuilder.appendFlag("Force");
        commandBuilder.appendFlag("Verbose");
        return commandBuilder.build();
    }
}