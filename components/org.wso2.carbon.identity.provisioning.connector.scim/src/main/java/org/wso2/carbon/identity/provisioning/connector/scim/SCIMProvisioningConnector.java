/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.provisioning.connector.scim;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.provisioning.AbstractOutboundProvisioningConnector;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningConstants;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningException;
import org.wso2.carbon.identity.provisioning.ProvisionedIdentifier;
import org.wso2.carbon.identity.provisioning.ProvisioningEntity;
import org.wso2.carbon.identity.provisioning.ProvisioningEntityType;
import org.wso2.carbon.identity.provisioning.ProvisioningOperation;
import org.wso2.carbon.identity.provisioning.ProvisioningUtil;
import org.wso2.carbon.identity.scim.common.impl.ProvisioningClient;
import org.wso2.carbon.identity.scim.common.utils.AttributeMapper;
import org.wso2.carbon.identity.scim.common.utils.SCIMCommonConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.charon.core.config.SCIMConfigConstants;
import org.wso2.charon.core.config.SCIMProvider;
import org.wso2.charon.core.exceptions.CharonException;
import org.wso2.charon.core.objects.Group;
import org.wso2.charon.core.objects.User;
import org.wso2.charon.core.schema.SCIMConstants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SCIMProvisioningConnector extends AbstractOutboundProvisioningConnector {

    private static final long serialVersionUID = -2800777564581005554L;
    private static Log log = LogFactory.getLog(SCIMProvisioningConnector.class);
    private SCIMProvider scimProvider;
    private String userStoreDomainName;
    private String scimVersion;

    @Override
    public void init(Property[] provisioningProperties) throws IdentityProvisioningException {
        scimProvider = new SCIMProvider();

        if (provisioningProperties != null && provisioningProperties.length > 0) {

            for (Property property : provisioningProperties) {

                if (SCIMProvisioningConnectorConstants.SCIM_USER_EP.equals(property.getName())) {
                    populateSCIMProvider(property, SCIMConfigConstants.ELEMENT_NAME_USER_ENDPOINT);
                } else if (SCIMProvisioningConnectorConstants.SCIM_GROUP_EP.equals(property.getName())) {
                    populateSCIMProvider(property, SCIMConfigConstants.ELEMENT_NAME_GROUP_ENDPOINT);
                } else if (SCIMProvisioningConnectorConstants.SCIM_USERNAME.equals(property.getName())) {
                    populateSCIMProvider(property, SCIMConfigConstants.ELEMENT_NAME_USERNAME);
                } else if (SCIMProvisioningConnectorConstants.SCIM_PASSWORD.equals(property.getName())) {
                    populateSCIMProvider(property, SCIMConfigConstants.ELEMENT_NAME_PASSWORD);
                } else if (SCIMProvisioningConnectorConstants.SCIM_USERSTORE_DOMAIN.equals(property.getName())) {
                    userStoreDomainName = property.getValue() != null ? property.getValue()
                            : property.getDefaultValue();
                } else if (SCIMProvisioningConnectorConstants.SCIM_ENABLE_PASSWORD_PROVISIONING.equals(property.getName())){
                    populateSCIMProvider(property, SCIMProvisioningConnectorConstants.SCIM_ENABLE_PASSWORD_PROVISIONING);
                } else if (SCIMProvisioningConnectorConstants.SCIM_DEFAULT_PASSWORD.equals(property.getName())){
                    populateSCIMProvider(property, SCIMProvisioningConnectorConstants.SCIM_DEFAULT_PASSWORD);
                } else if (SCIMProvisioningConnectorConstants.SCIM_VERSION.equals(property.getName())) {
                    scimVersion = property.getValue();
                }

                if (IdentityProvisioningConstants.JIT_PROVISIONING_ENABLED.equals(property
                        .getName()) && "1".equals(property.getValue())) {
                    jitProvisioningEnabled = true;
                }
            }
        }
    }

    @Override
    public ProvisionedIdentifier provision(ProvisioningEntity provisioningEntity)
            throws IdentityProvisioningException {

        if (provisioningEntity != null) {

            if (provisioningEntity.isJitProvisioning() && !isJitProvisioningEnabled()) {
                log.debug("JIT provisioning disabled for SCIM connector");
                return null;
            }

            if (provisioningEntity.getEntityType() == ProvisioningEntityType.USER) {
                if (provisioningEntity.getOperation() == ProvisioningOperation.DELETE) {
                    deleteUser(provisioningEntity);
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.POST) {
                    createUser(provisioningEntity);
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.PUT) {
                    updateUser(provisioningEntity, ProvisioningOperation.PATCH);
                } else if (provisioningEntity.getOperation() == ProvisioningOperation.PATCH) {
                    updateUser(provisioningEntity, ProvisioningOperation.PATCH);
                } else {
                    log.warn("Unsupported provisioning opertaion.");
                }

            } else if (provisioningEntity.getEntityType() == ProvisioningEntityType.GROUP) {
                if (StringUtils.isNotBlank(scimProvider.getProperties().get(SCIMConfigConstants
                        .ELEMENT_NAME_GROUP_ENDPOINT))) {
                    if (provisioningEntity.getOperation() == ProvisioningOperation.DELETE) {
                        deleteGroup(provisioningEntity);
                    } else if (provisioningEntity.getOperation() == ProvisioningOperation.POST) {
                        createGroup(provisioningEntity);
                    } else if (provisioningEntity.getOperation() == ProvisioningOperation.PUT) {
                        updateGroup(provisioningEntity);
                    } else if (provisioningEntity.getOperation() == ProvisioningOperation.PATCH) {
                        updateGroup(provisioningEntity);
                    } else {
                        log.warn("Unsupported provisioning operation.");
                    }
                } else {
                    log.info("SCIM group endpoint is not configured in Identity Provider configurations. Skip " +
                            "performing " + provisioningEntity.getOperation() + " for outbound group resource.");
                }
            } else {
                log.warn("Unsupported provisioning entity.");
            }
        }

        return null;

    }

    /**
     * @param userEntity
     * @throws IdentityProvisioningException
     */
    private void updateUser(ProvisioningEntity userEntity, ProvisioningOperation provisioningOperation) throws
            IdentityProvisioningException {

        try {

            List<String> userNames = getUserNames(userEntity.getAttributes());
            String userName = null;

            if (CollectionUtils.isNotEmpty(userNames)) {
                userName = userNames.get(0);
            }

            List<String> newGroupList = userEntity.getAttributes().get(ClaimMapping.build
                    (IdentityProvisioningConstants.NEW_GROUP_CLAIM_URI, null, null, false));
            List<String> deletedGroupList = userEntity.getAttributes().get(ClaimMapping.build
                    (IdentityProvisioningConstants.DELETED_GROUP_CLAIM_URI, null, null, false));

            if ((CollectionUtils.isNotEmpty(newGroupList)) || (CollectionUtils.isNotEmpty(deletedGroupList))) {
                if (log.isDebugEnabled()) {
                    log.debug("Groups of user : " + userName + " are updated. Groups assigned: " + newGroupList + ". " +
                            "" + "Groups unassigned: " + deletedGroupList);
                }

                if (StringUtils.isNotBlank(scimProvider.getProperties().get(SCIMConfigConstants
                        .ELEMENT_NAME_GROUP_ENDPOINT))) {

                    if (newGroupList != null) {
                        for (String newGroup : newGroupList) {
                            updateGroupsOfUser(userEntity, newGroup, true);
                        }
                    }

                    if (deletedGroupList != null) {
                        for (String deletedGroup : deletedGroupList) {
                            updateGroupsOfUser(userEntity, deletedGroup, false);
                        }
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("SCIM group endpoint is not configured in Identity Provider configurations. Skip "
                                + "initiating group updates for user: " + userName + " to groups assigned: " +
                                newGroupList + " and to groups unassigned: " + deletedGroupList);
                    }
                }
            } else {
                int httpMethod = SCIMConstants.POST;
                User user = null;

                filterUserMetaData(userEntity.getAttributes());
                // get single-valued claims
                Map<String, String> singleValued = getSingleValuedClaims(userEntity.getAttributes());

                // if user created through management console, claim values are not present.
                if (MapUtils.isNotEmpty(singleValued)) {
                    user = (User) AttributeMapper.constructSCIMObjectFromAttributes(singleValued, SCIMConstants
                            .USER_INT);
                } else {
                    user = new User();
                }

                user.setSchemaList(Arrays.asList(SCIMConstants.CORE_SCHEMA_URI));
                user.setUserName(userName);

                ProvisioningClient scimProvisioningClient = new ProvisioningClient(scimProvider, user, httpMethod,
                        null);

                if (ProvisioningOperation.PUT.equals(provisioningOperation)) {
                    scimProvisioningClient.provisionUpdateUser();
                } else if (ProvisioningOperation.PATCH.equals(provisioningOperation)) {
                    scimProvisioningClient.provisionPatchUser();
                }
            }
        } catch (Exception e) {
            throw new IdentityProvisioningException("Error while creating the user", e);
        }
    }

    /**
     * @param userEntity
     * @throws UserStoreException
     */
    private void createUser(ProvisioningEntity userEntity) throws IdentityProvisioningException {

        try {

            List<String> userNames = getUserNames(userEntity.getAttributes());
            String userName = null;

            if (CollectionUtils.isNotEmpty(userNames)) {
                userName = userNames.get(0);
            }

            int httpMethod = SCIMConstants.POST;
            User user = null;

            // get single-valued claims
            Map<String, String> singleValued = getSingleValuedClaims(userEntity.getAttributes());

            // if user created through management console, claim values are not present.
            user = (User) AttributeMapper.constructSCIMObjectFromAttributes(singleValued,
                    SCIMConstants.USER_INT);

            user.setSchemaList(Arrays.asList(SCIMConstants.CORE_SCHEMA_URI));
            user.setUserName(userName);
            setUserPassword(user, userEntity);

            ProvisioningClient scimProvsioningClient = new ProvisioningClient(scimProvider, user,
                    httpMethod, null);
            scimProvsioningClient.provisionCreateUser();

            List<String> newGroupList = userEntity.getAttributes().get(ClaimMapping.build
                    (IdentityProvisioningConstants.GROUP_CLAIM_URI, null, null, false));

            if (CollectionUtils.isNotEmpty(newGroupList)) {
                if (log.isDebugEnabled()) {
                    log.debug("User : " + userName + " is assigned to groups. Groups assigned: " + newGroupList);
                }

                if (StringUtils.isNotBlank(scimProvider.getProperties().get(SCIMConfigConstants
                        .ELEMENT_NAME_GROUP_ENDPOINT))) {
                    for (String newGroup : newGroupList) {
                        updateGroupsOfUser(userEntity, newGroup, true);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("SCIM group endpoint is not configured in Identity Provider configurations. Skip "
                                + "initiating group updates for user: " + userName + " to groups: " + newGroupList);
                    }
                }
            }

        } catch (Exception e) {
            throw new IdentityProvisioningException("Error while creating the user", e);
        }
    }

    /**
     * @param userEntity
     * @throws IdentityProvisioningException
     */
    private void deleteUser(ProvisioningEntity userEntity) throws IdentityProvisioningException {

        ProvisioningManager provisioningManager = new ProvisioningManager(
                SCIMProvisioningConnectorConstants.SCIM_VERSION1, userStoreDomainName);

        try {
            provisioningManager.deleteUser(userEntity, scimProvider);
        } catch (Exception e) {
            throw new IdentityProvisioningException("Error while deleting user.", e);
        }
    }

    /**
     * @param groupEntity
     * @return
     * @throws IdentityProvisioningException
     */
    private String createGroup(ProvisioningEntity groupEntity) throws IdentityProvisioningException {
        try {
            List<String> groupNames = getGroupNames(groupEntity.getAttributes());
            String groupName = null;

            if (CollectionUtils.isNotEmpty(groupNames)) {
                groupName = groupNames.get(0);
            }

            int httpMethod = SCIMConstants.POST;
            Group group = null;
            group = new Group();
            group.setSchemaList(Arrays.asList(SCIMConstants.CORE_SCHEMA_URI));
            group.setDisplayName(groupName);

            List<String> userList = getUserNames(groupEntity.getAttributes());

            if (CollectionUtils.isNotEmpty(userList)) {
                for (Iterator<String> iterator = userList.iterator(); iterator.hasNext(); ) {
                    String userName = iterator.next();
                    Map<String, Object> members = new HashMap<>();
                    members.put(SCIMConstants.CommonSchemaConstants.DISPLAY, userName);
                    group.setMember(members);
                }
            }

            ProvisioningClient scimProvsioningClient = new ProvisioningClient(scimProvider, group,
                    httpMethod, null);
            scimProvsioningClient.provisionCreateGroup();
        } catch (Exception e) {
            throw new IdentityProvisioningException("Error while adding group.", e);
        }

        return null;
    }

    /**
     * @param groupEntity
     * @throws IdentityProvisioningException
     */
    private void deleteGroup(ProvisioningEntity groupEntity) throws IdentityProvisioningException {
        try {

            List<String> groupNames = getGroupNames(groupEntity.getAttributes());
            String groupName = null;

            if (CollectionUtils.isNotEmpty(groupNames)) {
                groupName = groupNames.get(0);
            }

            int httpMethod = SCIMConstants.DELETE;
            Group group = null;

            group = new Group();
            group.setSchemaList(Arrays.asList(SCIMConstants.CORE_SCHEMA_URI));
            group.setDisplayName(groupName);

            ProvisioningClient scimProvsioningClient = new ProvisioningClient(scimProvider, group,
                    httpMethod, null);
            scimProvsioningClient.provisionDeleteGroup();

        } catch (Exception e) {
            throw new IdentityProvisioningException("Error while deleting group.", e);
        }
    }

    /**
     * @param groupEntity
     * @throws IdentityProvisioningException
     */
    private void updateGroup(ProvisioningEntity groupEntity) throws IdentityProvisioningException {
        try {

            List<String> groupNames = getGroupNames(groupEntity.getAttributes());
            String groupName = null;

            if (CollectionUtils.isNotEmpty(groupNames)) {
                groupName = groupNames.get(0);
            }

            int httpMethod = SCIMConstants.PUT;
            Group group = new Group();
            group.setSchemaList(Arrays.asList(SCIMConstants.CORE_SCHEMA_URI));
            group.setDisplayName(groupName);

            List<String> userList = getUserNames(groupEntity.getAttributes());
            if (CollectionUtils.isNotEmpty(userList)) {
                for (Iterator<String> iterator = userList.iterator(); iterator.hasNext(); ) {
                    String userName = iterator.next();
                    Map<String, Object> members = new HashMap<>();
                    members.put(SCIMConstants.CommonSchemaConstants.DISPLAY, userName);
                    group.setMember(members);
                }
            }

            List<String> deletedUserList = getDeletedUserNames(groupEntity.getAttributes());
            if (CollectionUtils.isNotEmpty(deletedUserList)) {
                for (String deletedUser : deletedUserList) {
                    Map<String, Object> member = new HashMap<>();
                    member.put(SCIMConstants.CommonSchemaConstants.DISPLAY, deletedUser);
                    member.put(SCIMConstants.CommonSchemaConstants.OPERATION, SCIMConstants.CommonSchemaConstants
                            .OPERATION_DELETE);
                    group.setMember(member);
                }
            }

            String oldGroupName = ProvisioningUtil.getAttributeValue(groupEntity, IdentityProvisioningConstants
                    .OLD_GROUP_NAME_CLAIM_URI);
            ProvisioningClient scimProvsioningClient = null;
            if (StringUtils.isEmpty(oldGroupName)) {
                scimProvsioningClient = new ProvisioningClient(scimProvider, group, httpMethod, null);
            } else {
                Map<String, Object> additionalInformation = new HashMap();
                additionalInformation.put(SCIMCommonConstants.IS_ROLE_NAME_CHANGED_ON_UPDATE, true);
                additionalInformation.put(SCIMCommonConstants.OLD_GROUP_NAME, oldGroupName);
                scimProvsioningClient = new ProvisioningClient(scimProvider, group, httpMethod, additionalInformation);
            }
            if (ProvisioningOperation.PUT.equals(groupEntity.getOperation())) {
                scimProvsioningClient.provisionUpdateGroup();
            }else if(ProvisioningOperation.PATCH.equals(groupEntity.getOperation())){
                scimProvsioningClient.provisionPatchGroup();
            }
        } catch (Exception e) {
            throw new IdentityProvisioningException("Error while updating group.", e);
        }
    }

    @Override
    protected String getUserStoreDomainName() {
        return userStoreDomainName;
    }

    /**
     * @param property
     * @param scimPropertyName
     * @throws IdentityProvisioningException
     */
    private void populateSCIMProvider(Property property, String scimPropertyName)
            throws IdentityProvisioningException {

        if (property.getValue() != null && property.getValue().length() > 0) {
            scimProvider.setProperty(scimPropertyName, property.getValue());
        } else if (property.getDefaultValue() != null) {
            scimProvider.setProperty(scimPropertyName, property.getDefaultValue());
        }
    }

    @Override
    public String getClaimDialectUri() throws IdentityProvisioningException {
        return SCIMProvisioningConnectorConstants.DEFAULT_SCIM_DIALECT;
    }

    public boolean isEnabled() throws IdentityProvisioningException {
        return true;
    }

    private void setUserPassword(User user, ProvisioningEntity userEntity) throws CharonException {
        if ("true".equals(scimProvider.getProperty(SCIMProvisioningConnectorConstants.SCIM_ENABLE_PASSWORD_PROVISIONING))) {
            user.setPassword(getPassword(userEntity.getAttributes()));
        } else if (StringUtils.isNotBlank(scimProvider.getProperty(SCIMProvisioningConnectorConstants.SCIM_DEFAULT_PASSWORD))) {
            user.setPassword(scimProvider.getProperty(SCIMProvisioningConnectorConstants.SCIM_DEFAULT_PASSWORD));
        }
    }

    private void updateGroupsOfUser(ProvisioningEntity userEntity, String groupName, boolean newGroup) throws
            IdentityProvisioningException {

        String[] userList = {userEntity.getEntityName()};

        Map<ClaimMapping, List<String>> outboundAttributes = new HashMap<>();
        outboundAttributes.put(ClaimMapping.build(IdentityProvisioningConstants.GROUP_CLAIM_URI, null, null,
                false), Arrays.asList(groupName));
        if (newGroup) {
            outboundAttributes.put(ClaimMapping.build(IdentityProvisioningConstants.USERNAME_CLAIM_URI, null, null,
                    false), Arrays.asList(userList));
        } else {
            outboundAttributes.put(ClaimMapping.build(IdentityProvisioningConstants.DELETED_USER_CLAIM_URI, null,
                    null, false), Arrays.asList(userList));
        }

        ProvisioningEntity provisioningEntity = new ProvisioningEntity(ProvisioningEntityType.GROUP, groupName,
                ProvisioningOperation.PATCH, outboundAttributes);

        updateGroup(provisioningEntity);
    }

    private List<String> getDeletedUserNames(Map<ClaimMapping, List<String>> attributeMap) {
        return ProvisioningUtil.getClaimValues(attributeMap, IdentityProvisioningConstants.DELETED_USER_CLAIM_URI,
                this.getUserStoreDomainName());
    }

    private void filterUserMetaData(Map<ClaimMapping, List<String>> attributes) {

        for (Iterator<Map.Entry<ClaimMapping, List<String>>> iterator = attributes.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<ClaimMapping, List<String>> entry = iterator.next();
            if (SCIMConstants.META_CREATED_URI.equals(entry.getKey().getLocalClaim().getClaimUri()) ||
                    SCIMConstants.ID_URI.equals(entry.getKey().getLocalClaim().getClaimUri()) ||
                    SCIMConstants.META_LOCATION_URI.equals(entry.getKey().getLocalClaim().getClaimUri()) ||
                    SCIMConstants.META_LAST_MODIFIED_URI.equals(entry.getKey().getLocalClaim().getClaimUri())) {
                iterator.remove();
            }
        }
    }
}
