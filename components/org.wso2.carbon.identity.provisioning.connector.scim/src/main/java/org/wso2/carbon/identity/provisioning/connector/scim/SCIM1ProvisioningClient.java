package org.wso2.carbon.identity.provisioning.connector.scim;

import org.wso2.carbon.identity.scim.common.impl.ProvisioningClient;
import org.wso2.carbon.identity.scim.common.utils.IdentitySCIMException;
import org.wso2.charon.core.config.SCIMProvider;
import org.wso2.charon.core.exceptions.CharonException;
import org.wso2.charon.core.objects.User;
import org.wso2.charon.core.schema.SCIMConstants;

import java.util.Arrays;

public class SCIM1ProvisioningClient {

    public void deleteUser(String userName, SCIMProvider scimProvider) throws IdentitySCIMException,
            CharonException {

        int httpMethod = SCIMConstants.DELETE;
        User user = new User();
        user.setSchemaList(Arrays.asList(SCIMConstants.CORE_SCHEMA_URI));
        user.setUserName(userName);
        ProvisioningClient scimProvsioningClient = new ProvisioningClient(scimProvider, user,
                httpMethod, null);
        scimProvsioningClient.provisionDeleteUser();

    }
}
