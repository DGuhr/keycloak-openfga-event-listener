package io.dguhr.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dguhr.keycloak.service.ServiceHandler;
import io.dguhr.keycloak.event.SpiceDbEventParser;
import io.dguhr.keycloak.model.AuthorizationModel;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

public class SpiceDbEventListenerProvider implements EventListenerProvider {
	private static final Logger LOG = Logger.getLogger(SpiceDbEventListenerProvider.class);
	private ObjectMapper mapper;
	private ServiceHandler service;

	private AuthorizationModel model;
	private KeycloakSession session;

	public SpiceDbEventListenerProvider(AuthorizationModel model, ServiceHandler service, KeycloakSession session) {
		LOG.info("[SpiceDbEventListener] SpiceDbEventListenerProvider initializing...");
		this.service = service;
		this.session = session;
		this.model = model;
		LOG.info("[SpiceDbEventListener] SpiceDbEventListenerProvider initialized with model: " + model.toString());
		mapper = new ObjectMapper();
	}

	@Override
	public void onEvent(Event event) {
		LOG.debug("[SpiceDbEventListener] onEvent type: " + event.getType().toString());
		LOG.debug("[SpiceDbEventListener] Discarding event...");
	}

	@Override
	public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
		LOG.debug("[SpiceDbEventListener] onEvent Admin received events");

		try {
			LOG.debugf("[SpiceDbEventListener] admin event: " + mapper.writeValueAsString(adminEvent));
			SpiceDbEventParser spiceDbEventParser = new SpiceDbEventParser(adminEvent, model, session);
			LOG.debugf("[SpiceDbEventListener] event received: " + spiceDbEventParser.toString());
			service.handle(adminEvent.getId(), mapper.writeValueAsString(spiceDbEventParser.toTupleEvent()));
		} catch (IllegalArgumentException e) {
			LOG.warn(e.getMessage());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void close() {
		// ignore
	}
}
