/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */

package io.liveoak.security.policy.uri.integration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.liveoak.common.DefaultRequestAttributes;
import io.liveoak.common.DefaultResourceParams;
import io.liveoak.common.DefaultSecurityContext;
import io.liveoak.common.codec.DefaultResourceState;
import io.liveoak.common.security.AuthzConstants;
import io.liveoak.common.security.AuthzDecision;
import io.liveoak.spi.RequestAttributes;
import io.liveoak.spi.RequestContext;
import io.liveoak.spi.RequestType;
import io.liveoak.spi.ResourcePath;
import io.liveoak.spi.SecurityContext;
import io.liveoak.spi.resource.RootResource;
import io.liveoak.spi.state.ResourceState;
import io.liveoak.testtools.AbstractResourceTestCase;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class URIPolicyRootResourceTest extends AbstractResourceTestCase {

    @Override
    public RootResource createRootResource() {
        return new URIPolicyRootResource("uriPolicy");
    }

    @Override
    public ResourceState createConfig() {
        ResourceState state = super.createConfig();
        state.putProperty("policy-config", System.getProperty("user.dir") + "/src/test/resources/policy-config/uri-policy-config.json");
        return state;
    }

    @Test
    public void testURIPolicyServiceRequest() throws Exception {
        RequestContext reqCtx = new RequestContext.Builder().build();
        ResourceState state = client.read(reqCtx, "/uriPolicy");
        boolean authzCheckFound = false;
        for (ResourceState member : state.members()) {
            if (member.id().equals("authzCheck")) {
                authzCheckFound = true;
                break;
            }
        }
        Assert.assertTrue("Child resource 'authzCheck' not found", authzCheckFound);
    }

    @Test
    public void testAuthzCheckNullRequestContext() throws Exception {
        assertAuthzDecision(null, AuthzDecision.REJECT);
    }

    @Test
    public void testAuthorizationRequest() throws Exception {
        // create some sample securityContext instances
        SecurityContext anonymous = new DefaultSecurityContext();
        DefaultSecurityContext admin = new DefaultSecurityContext();
        Set<String> s1 = new HashSet();
        s1.addAll(Arrays.asList(new String[]{"test-app/admin", "test-app/user"}));
        admin.setRealm("default");
        admin.setSubject("admin");
        admin.setRoles(s1);

        DefaultSecurityContext user = new DefaultSecurityContext();
        Set<String> s2 = new HashSet();
        s2.addAll(Arrays.asList(new String[]{"test-app/user"}));
        user.setRealm("default");
        user.setSubject("john");
        user.setRoles(s2);

        // request to /app/some should be IGNORED for anonymous user, but allowed for user or admin
        RequestContext.Builder appReq = new RequestContext.Builder().requestType(RequestType.READ)
                .resourcePath(new ResourcePath("/app/some"));
        assertAuthzDecision(appReq.securityContext(anonymous), AuthzDecision.IGNORE);
        assertAuthzDecision(appReq.securityContext(admin), AuthzDecision.ACCEPT);
        assertAuthzDecision(appReq.securityContext(user), AuthzDecision.ACCEPT);

        // CREATE request to app should be ACCEPT just for admin
        appReq.requestType(RequestType.CREATE);
        assertAuthzDecision(appReq.securityContext(anonymous), AuthzDecision.IGNORE);
        assertAuthzDecision(appReq.securityContext(admin), AuthzDecision.ACCEPT);
        assertAuthzDecision(appReq.securityContext(user), AuthzDecision.IGNORE);

        // Request to public page allowed for everyone
        RequestContext.Builder publicReq = new RequestContext.Builder().requestType(RequestType.READ)
                .resourcePath(new ResourcePath("/public/some"));
        assertAuthzDecision(publicReq.securityContext(anonymous), AuthzDecision.ACCEPT);
        assertAuthzDecision(publicReq.securityContext(admin), AuthzDecision.ACCEPT);
        assertAuthzDecision(publicReq.securityContext(user), AuthzDecision.ACCEPT);

        // Request to /storage/some should be IGNORED for anonymous user, but allowed for user or admin in case that query contains username
        RequestContext.Builder storageReq = new RequestContext.Builder().requestType(RequestType.READ)
                .resourcePath(new ResourcePath("/storage/some"));
        Map<String, List<String>> params = new HashMap<>();
        params.put("q", Arrays.asList("{\"completed\":\"false\",\"user\":\"john\"}"));
        storageReq.resourceParams(DefaultResourceParams.instance(params));
        assertAuthzDecision(storageReq.securityContext(anonymous), AuthzDecision.IGNORE);
        assertAuthzDecision(storageReq.securityContext(admin), AuthzDecision.ACCEPT);
        assertAuthzDecision(storageReq.securityContext(user), AuthzDecision.ACCEPT);

        // IGNORED for user if token from query is different from username
        params.put("q", Arrays.asList("{\"completed\":\"false\",\"user\":\"otherUser\"}"));
        storageReq.resourceParams(DefaultResourceParams.instance(params));
        assertAuthzDecision(storageReq.securityContext(user), AuthzDecision.IGNORE);

        // CREATE request is IGNORED for anonymous and allowed for admin. It's allowed for user if "user" from createState equals username
        storageReq = new RequestContext.Builder().requestType(RequestType.CREATE).resourcePath(new ResourcePath("/storage/some"));
        ResourceState createState = new DefaultResourceState();
        createState.putProperty("user", "john");
        createState.putProperty("something", "something-which-does-not-matter");
        assertAuthzDecision(storageReq.securityContext(anonymous), createState, AuthzDecision.IGNORE);
        assertAuthzDecision(storageReq.securityContext(admin), createState, AuthzDecision.ACCEPT);
        assertAuthzDecision(storageReq.securityContext(user), createState, AuthzDecision.ACCEPT);

        // CREATE not allowed for user without createState
        assertAuthzDecision(storageReq.securityContext(user), AuthzDecision.IGNORE);

        // CREATE request not allowed for user if "user" from createState doesn't equal username
        createState.putProperty("user", "otherUser");
        assertAuthzDecision(storageReq.securityContext(admin), createState, AuthzDecision.ACCEPT);
        assertAuthzDecision(storageReq.securityContext(user), createState, AuthzDecision.IGNORE);

        // UPDATE to storage conforms same rules like CREATE
        createState.putProperty("user", "john");
        storageReq = new RequestContext.Builder().requestType(RequestType.UPDATE).resourcePath(new ResourcePath("/storage/some"));
        assertAuthzDecision(storageReq.securityContext(anonymous), createState, AuthzDecision.IGNORE);
        assertAuthzDecision(storageReq.securityContext(admin), createState, AuthzDecision.ACCEPT);
        assertAuthzDecision(storageReq.securityContext(user), createState, AuthzDecision.ACCEPT);
        createState.putProperty("user", "otherUser");
        assertAuthzDecision(storageReq.securityContext(admin), createState, AuthzDecision.ACCEPT);
        assertAuthzDecision(storageReq.securityContext(user), createState, AuthzDecision.IGNORE);

    }

    private void assertAuthzDecision(RequestContext reqCtxToCheck, AuthzDecision expectedDecision) throws Exception {
        assertAuthzDecision(reqCtxToCheck, null, expectedDecision);
    }

    private void assertAuthzDecision(RequestContext reqCtxToCheck, ResourceState reqResourceState, AuthzDecision expectedDecision) throws Exception {
        RequestAttributes attribs = new DefaultRequestAttributes();
        attribs.setAttribute(AuthzConstants.ATTR_REQUEST_CONTEXT, reqCtxToCheck);
        attribs.setAttribute(AuthzConstants.ATTR_REQUEST_RESOURCE_STATE, reqResourceState);
        RequestContext reqCtx = new RequestContext.Builder().requestAttributes(attribs).build();
        ResourceState state = client.read(reqCtx, "/uriPolicy/authzCheck");
        String decision = (String) state.getProperty(AuthzConstants.ATTR_AUTHZ_POLICY_RESULT);
        Assert.assertNotNull(decision);
        Assert.assertEquals(expectedDecision, Enum.valueOf(AuthzDecision.class, decision));
    }
}
