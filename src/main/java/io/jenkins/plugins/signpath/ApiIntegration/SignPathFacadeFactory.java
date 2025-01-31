package io.jenkins.plugins.signpath.ApiIntegration;

/**
 * A factory that creates a SignPathFacade that is bound to the given parameters
 */
public interface SignPathFacadeFactory {
    /**
     * Creates a SignPathFacade that is bound to the credentials parameter to use for authenticating against the SignPath API endpoint
     */
    SignPathFacade create(SignPathCredentials credentials);
}