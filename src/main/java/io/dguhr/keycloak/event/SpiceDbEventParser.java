package io.dguhr.keycloak.event;

import com.authzed.api.v1.Core;
import com.authzed.api.v1.PermissionService;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.grpcutil.BearerToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dguhr.keycloak.model.ObjectRelation;
import io.dguhr.keycloak.model.OpenFgaTupleEvent;
import io.dguhr.keycloak.model.ZanzibarTupleEvent;
import io.grpc.ManagedChannelBuilder;
import org.jboss.logging.Logger;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import io.grpc.ManagedChannel;

public class SpiceDbEventParser {

    public static final String EVT_RESOURCE_USERS = "users";
    public static final String EVT_RESOURCE_GROUPS = "groups";
    public static final String EVT_RESOURCE_ROLES_BY_ID = "roles-by-id";
    public static final String OBJECT_TYPE_USER = "user";
    public static final String OBJECT_TYPE_ROLE = "role";
    public static final String OBJECT_TYPE_GROUP = "group";

    private AdminEvent event;
    private ObjectMapper mapper;
    private KeycloakSession session;

    private static final Logger logger = Logger.getLogger(SpiceDbEventParser.class);

    public SpiceDbEventParser(AdminEvent event){
        this.event = event;
        this.mapper = new ObjectMapper();
    }
    public SpiceDbEventParser(AdminEvent event, KeycloakSession session){
        this.event = event;
        this.session = session;
        this.mapper = new ObjectMapper();
    }

    /***
     * Convert the Keycloak event to Event Tuple following the OpenFGA specs
     * The OpenFGA authorization model is more complex, nevertheless, here is a simplified version of the Authorization Model that fit our requirements'
     * role
     *   |_ assignee     --> user   == Keycloak User Role Assignment
     *   |_ parent_group --> group  == Keycloak Group Role Assignment
     *   |_ parent       --> role   == Keycloak Role to Role Assignment
     *  group
     *   |_ assignee     --> user   == Keycloak User Group Role Assignment
     */
    public ZanzibarTupleEvent toTupleEvent()
    {
        // Get all the required information from the KC event
        String evtObjType  = getEventObjectType();
        String evtUserType = getEventUserType();
        String evtUserId   = evtUserType.equals(OBJECT_TYPE_ROLE) ? findRoleNameInRealm(getEventUserId()) : getEventUserId();
        String evtObjectId = getEventObjectName();
        String evtOrgId = findOrgIdOfUserId(evtUserId);

        logger.debug("[SpiceDbEventListener] TYPE OF EVENT IS: " + event.getResourceTypeAsString());
        logger.debug("[SpiceDbEventListener] ORG ID FOR USER IN EVENT IS: " + evtOrgId);
        logger.debug("[SpiceDbEventListener] EVENTS object type IS: " + evtObjType);
        logger.debug("[SpiceDbEventListener] EVENTS user type IS: " + evtUserType);
        logger.debug("[SpiceDbEventListener] EVENTS user ID IS: " + evtUserId);
        logger.debug("[SpiceDbEventListener] EVENTS onbject ID IS: " + evtObjectId);
        logger.debug("[SpiceDbEventListener] EVENT represantation is: " + event.getRepresentation());

        //TODO use the spicedb client
        // Check if the type (objectType) and object (userType) is present in the authorization model
        // So far, every relation between the type and the object is UNIQUE
        //ObjectRelation objectRelation = model.filterByType(evtObjType).filterByObject(evtUserType);

        return new OpenFgaTupleEvent.Builder()
              .objectType(evtObjType)
              .withObjectRelation(new ObjectRelation("related","foo"))
              .userId(evtUserId)
              .objectId(evtObjectId)
              .operation(getEventOperation())
              .build();
    }

    /**
     * Checks for group_membership events.
     * @return object type or error
     */
    public String getEventObjectType() {
        switch (event.getResourceType()) {
            //remove roles from the game for now. TODO: check if wanted.
            /*case REALM_ROLE_MAPPING:
            case REALM_ROLE:
                return OBJECT_TYPE_ROLE;*/
            case GROUP_MEMBERSHIP:
                return OBJECT_TYPE_GROUP;
            default:
                throw new IllegalArgumentException("Event is not handled, id:" + event.getId() + " resource name: " + event.getResourceType().name());
        }
    }

    public String findOrgIdOfUserId(String userId) {
        logger.debug("Finding org_id for userId: " + userId);
        String orgId = session.users().getUserById(session.getContext().getRealm(), userId).getFirstAttribute("org_id");
        logger.debug("Found org_id: " + orgId +" for userId: " + userId);

        return orgId;
    }

    /**
     * perhaps rename to getEventSubjectType?
     * @return
     */
    public String getEventUserType() {
        switch (getEventResourceName()) {
            case EVT_RESOURCE_USERS:
                return OBJECT_TYPE_USER;
            case EVT_RESOURCE_GROUPS:
                return OBJECT_TYPE_GROUP;
            case EVT_RESOURCE_ROLES_BY_ID:
                return OBJECT_TYPE_ROLE;
            default:
                throw new IllegalArgumentException("Resource type is not handled: " + event.getOperationType());
        }
    }

    /**
     * //TODO: rename + extend for update cases?
     * @return
     */
    public String getEventOperation() {
        switch (event.getOperationType()) {
            case CREATE:
                return OpenFgaTupleEvent.OPERATION_WRITES;
            case DELETE:
                return OpenFgaTupleEvent.OPERATION_DELETES;
            default:
                throw new IllegalArgumentException("Unknown operation type: " + event.getOperationType());
        }
    }

    public String getEventAuthenticatedUserId() {
        return this.event.getAuthDetails().getUserId();
    }

    public String getEventUserId() {
        return this.event.getResourcePath().split("/")[1];
    }

    public String getEventResourceName() {
        return this.event.getResourcePath().split("/")[0];
    }

    public Boolean isUserEvent() {
        return getEventResourceName().equalsIgnoreCase(EVT_RESOURCE_USERS);
    }

    public Boolean isRoleEvent() {
        return getEventResourceName().equalsIgnoreCase(EVT_RESOURCE_ROLES_BY_ID);
    }

    public Boolean isGroupEvent() {
        return getEventResourceName().equalsIgnoreCase(EVT_RESOURCE_GROUPS);
    }

    public String getEventObjectId() {
        return getObjectByAttributeName("id");
    }

    public String getEventObjectName() {
        return getObjectByAttributeName("name");
    }

    private String getObjectByAttributeName(String attributeName) {
        ObjectMapper mapper = new ObjectMapper();
        String representation = event.getRepresentation().replaceAll("\\\\", ""); // Fixme: I should try to avoid the replace
        try {
            JsonNode jsonNode = mapper.readTree(representation);
            if(jsonNode.isArray()){
                return jsonNode.get(0).get(attributeName).asText();
            }
            return jsonNode.get(attributeName).asText();
        }
        catch (JsonMappingException e) {
            throw new RuntimeException(e); // Fixme: Improve exception handling
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String findRoleNameInRealm(String roleId)  {
        logger.debug("Finding role name by role id: " +  roleId);
        return session.getContext().getRealm().getRoleById(roleId).getName();
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("AdminEvent resourceType=");
        sb.append(event.getResourceType());
        sb.append(", operationType=");
        sb.append(event.getOperationType());
        sb.append(", realmId=");
        sb.append(event.getAuthDetails().getRealmId());
        sb.append(", clientId=");
        sb.append(event.getAuthDetails().getClientId());
        sb.append(", userId=");
        sb.append(event.getAuthDetails().getUserId());
        sb.append(", ipAddress=");
        sb.append(event.getAuthDetails().getIpAddress());
        sb.append(", resourcePath=");
        sb.append(event.getResourcePath());
        if (event.getError() != null) {
            sb.append(", error=");
            sb.append(event.getError());
        }
        return sb.toString();
    }

    //TODO: check this out.

    public String test () {
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("localhost:50051") // TODO: create local setup and make it configurable
                .usePlaintext() // if not using TLS, replace with .usePlaintext()
                .build();
        PermissionsServiceGrpc.PermissionsServiceBlockingStub permissionService = PermissionsServiceGrpc.newBlockingStub(channel)
                .withCallCredentials(new BearerToken("t_your_token_here_1234567deadbeef")); //TODO configurable

        PermissionService.WriteRelationshipsRequest request = PermissionService.WriteRelationshipsRequest.newBuilder().addUpdates(
                        com.authzed.api.v1.Core.RelationshipUpdate.newBuilder()
                                .setOperation(Core.RelationshipUpdate.Operation.OPERATION_CREATE)
                                .setRelationship(
                                        Core.Relationship.newBuilder()
                                                .setResource(
                                                        Core.ObjectReference.newBuilder()
                                                                .setObjectType("thelargeapp/article")
                                                                .setObjectId("java_test")
                                                                .build())
                                                .setRelation("author")
                                                .setSubject(
                                                        Core.SubjectReference.newBuilder()
                                                                .setObject(
                                                                        Core.ObjectReference.newBuilder()
                                                                                .setObjectType("thelargeapp/user")
                                                                                .setObjectId("george")
                                                                                .build())
                                                                .build())
                                                .build())
                                .build())
                .build();


        PermissionService.WriteRelationshipsResponse response;
        try {
            response = permissionService.writeRelationships(request);
        } catch (Exception e) {
            logger.warn("RPC in writeRelationship failed: ", e);
            return "";
        }
        logger.info("Response: " + response.toString());
        return response.getWrittenAt().getToken();
    }

}
