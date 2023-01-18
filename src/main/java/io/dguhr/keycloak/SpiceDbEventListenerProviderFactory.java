package io.dguhr.keycloak;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dguhr.keycloak.service.ServiceHandler;
import io.dguhr.keycloak.service.ServiceHandlerFactory;
import io.dguhr.keycloak.model.AuthorizationModel;
import org.keycloak.Config.Scope;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class SpiceDbEventListenerProviderFactory implements EventListenerProviderFactory {

	private static final String PROVIDER_ID = "spicedb-events";
	private SpiceDbEventListenerProvider instance;
	private String serviceHandlerName;
	private AuthorizationModel model;
	private Scope config;

	@Override
	public EventListenerProvider create(KeycloakSession session) {
		if (instance == null) {
			ServiceHandler serviceHandler = ServiceHandlerFactory.create(serviceHandlerName, session, config);
			serviceHandler.validateConfig();
			instance = new SpiceDbEventListenerProvider(model, serviceHandler, session);
		}
		return instance;
	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}

	@Override
	public void init(Scope config) {
		this.serviceHandlerName = config.get("serviceHandlerName");
		if (serviceHandlerName == null) {
			throw new NullPointerException("Service handler name must not be null.");
		}

		String authorizationModelJson = config.get("authorizationModel");
		if (authorizationModelJson == null) {
			throw new NullPointerException("Authorization Model must not be null.");
		}

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			this.model = objectMapper.readValue(authorizationModelJson, AuthorizationModel.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Authorization Model is not valid: " + e.getMessage());
		}
		this.config = config;
	}

	@Override
	public void postInit(KeycloakSessionFactory arg0) {
		// ignore
	}

	@Override
	public void close() {
		// ignore
	}
}
