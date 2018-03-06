package org.wso2.carbon.identity.provisioning.connector.scim;

import org.apache.commons.collections.CollectionUtils;
import org.wso2.carbon.identity.provisioning.IdentityProvisioningConstants;
import org.wso2.carbon.identity.provisioning.ProvisioningEntity;
import org.wso2.carbon.identity.provisioning.ProvisioningUtil;
import org.wso2.carbon.identity.scim.common.utils.IdentitySCIMException;
import org.wso2.charon.core.config.SCIMProvider;
import org.wso2.charon.core.exceptions.CharonException;

import java.util.List;

public class ProvisioningManager {

    private String scimVersion;
    private String userStoreDomainName;

    ProvisioningManager(String scimVersion, String userStoreDomainName) {

        this.scimVersion = scimVersion;
        this.userStoreDomainName = userStoreDomainName;
    }

    public void deleteUser(ProvisioningEntity userEntity, SCIMProvider scimProvider) throws CharonException, IdentitySCIMException {

        String userName = getUserName(userEntity);

        if(this.scimVersion.equals(SCIMProvisioningConnectorConstants.SCIM_VERSION1)) {
            SCIM1ProvisioningClient client = new SCIM1ProvisioningClient();
            client.deleteUser(userName, scimProvider);
        } else {

        }

    }

    private String getUserName(ProvisioningEntity userEntity) {

        List<String> userNames = ProvisioningUtil.getClaimValues(userEntity.getAttributes(),
                IdentityProvisioningConstants.USERNAME_CLAIM_URI, this.userStoreDomainName);
        String userName = null;

        if (CollectionUtils.isNotEmpty(userNames)) {
            userName = userNames.get(0);
        }

        return userName;
    }
}
