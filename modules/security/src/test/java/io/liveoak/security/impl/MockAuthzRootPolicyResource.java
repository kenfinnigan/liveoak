package io.liveoak.security.impl;

import io.liveoak.common.security.AuthzConstants;
import io.liveoak.common.security.AuthzDecision;
import io.liveoak.spi.RequestContext;
import io.liveoak.spi.resource.RootResource;
import io.liveoak.spi.resource.SynchronousResource;
import io.liveoak.spi.resource.async.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class MockAuthzRootPolicyResource implements RootResource, SynchronousResource {

    private Resource parent;
    private String id;
    private BlockingQueue<RequestContext> requests = new LinkedBlockingDeque<>();
    private AuthzDecision authzDecision = AuthzDecision.IGNORE;

    public MockAuthzRootPolicyResource(String id) {
        this.id = id;
    }

    @Override
    public void parent(Resource parent) {
        this.parent = parent;
    }

    @Override
    public Resource parent() {
        return this.parent;
    }

    @Override
    public String id() {
        return id;
    }

    public void setAuthzDecision(AuthzDecision authzDecision) {
        this.authzDecision = authzDecision;
    }

    public RequestContext pollRequest(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return requests.poll(timeout, timeUnit);
    }

    @Override
    public Map<String, ?> properties(RequestContext ctx) throws Exception {
        Map<String, String> result = new HashMap<>();
        result.put(AuthzConstants.ATTR_AUTHZ_POLICY_RESULT, authzDecision.toString());
        return result;
    }

}
