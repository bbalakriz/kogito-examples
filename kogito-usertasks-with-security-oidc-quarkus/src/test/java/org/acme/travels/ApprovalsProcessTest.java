package org.acme.travels;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.acme.test.KeycloakServerTestResource;
import org.jbpm.process.instance.impl.humantask.HumanTaskTransition;
import org.jbpm.process.instance.impl.humantask.phases.Claim;
import org.jbpm.process.instance.impl.workitem.Complete;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Model;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.services.identity.StaticIdentityProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(KeycloakServerTestResource.class)
public class ApprovalsProcessTest {

    @Named("approvals")
    @Inject
    Process<? extends Model> approvalsProcess;

    @Test
    public void testApprovalProcess() {

        assertNotNull(approvalsProcess);

        Model m = approvalsProcess.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traveller", new Traveller("John", "Doe", "john.doe@example.com", "American", new Address("main street", "Boston", "10005", "US")));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = approvalsProcess.createInstance(m);
        processInstance.start();
        assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE, processInstance.status());

        StaticIdentityProvider identity = new StaticIdentityProvider("admin", Collections.singletonList("managers"));
        SecurityPolicy policy = SecurityPolicy.of(identity);

        processInstance.workItems(policy);

        List<WorkItem> workItems = processInstance.workItems(policy);
        assertEquals(1, workItems.size());
        Map<String, Object> results = new HashMap<>();
        results.put("approved", true);
        processInstance.completeWorkItem(workItems.get(0).getId(), results, policy);

        workItems = processInstance.workItems(policy);
        assertEquals(0, workItems.size());

        identity = new StaticIdentityProvider("john", Collections.singletonList("managers"));
        policy = SecurityPolicy.of(identity);

        processInstance.workItems(policy);

        workItems = processInstance.workItems(policy);
        assertEquals(1, workItems.size());

        results.put("approved", false);
        processInstance.completeWorkItem(workItems.get(0).getId(), results, policy);
        assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, processInstance.status());

        Model result = (Model) processInstance.variables();
        assertEquals(4, result.toMap().size());
        assertEquals(result.toMap().get("approver"), "admin");
        assertEquals(result.toMap().get("firstLineApproval"), true);
        assertEquals(result.toMap().get("secondLineApproval"), false);
    }

    @Test
    public void testApprovalProcessViaPhases() {

        assertNotNull(approvalsProcess);

        Model m = approvalsProcess.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("traveller", new Traveller("John", "Doe", "john.doe@example.com", "American", new Address("main street", "Boston", "10005", "US")));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = approvalsProcess.createInstance(m);
        processInstance.start();
        assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE, processInstance.status());

        StaticIdentityProvider identity = new StaticIdentityProvider("admin", Collections.singletonList("managers"));
        SecurityPolicy policy = SecurityPolicy.of(identity);

        processInstance.workItems(policy);

        List<WorkItem> workItems = processInstance.workItems(policy);
        assertEquals(1, workItems.size());

        processInstance.transitionWorkItem(workItems.get(0).getId(), new HumanTaskTransition(Claim.ID, null, policy));
        processInstance.transitionWorkItem(workItems.get(0).getId(), new HumanTaskTransition(Complete.ID, Collections.singletonMap("approved", true), policy));

        workItems = processInstance.workItems(policy);
        assertEquals(0, workItems.size());

        identity = new StaticIdentityProvider("john", Collections.singletonList("managers"));
        policy = SecurityPolicy.of(identity);

        processInstance.workItems(policy);

        workItems = processInstance.workItems(policy);
        assertEquals(1, workItems.size());

        processInstance.transitionWorkItem(workItems.get(0).getId(), new HumanTaskTransition(Claim.ID, null, policy));
        processInstance.transitionWorkItem(workItems.get(0).getId(), new HumanTaskTransition(Complete.ID, Collections.singletonMap("approved", false), policy));

        assertEquals(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED, processInstance.status());

        Model result = (Model) processInstance.variables();
        assertEquals(4, result.toMap().size());
        assertEquals(result.toMap().get("approver"), "admin");
        assertEquals(result.toMap().get("firstLineApproval"), true);
        assertEquals(result.toMap().get("secondLineApproval"), false);
    }
}
