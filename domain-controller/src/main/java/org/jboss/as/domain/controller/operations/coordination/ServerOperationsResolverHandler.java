/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller.operations.coordination;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.ModelUpdateOperationHandler;
import org.jboss.as.controller.NewOperationContext;
import org.jboss.as.controller.NewStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.ModelNodeRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;

/**
 * Adds to the response the server-level operations needed to effect the given domain/host operation on the
 * servers controlled by this host.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerOperationsResolverHandler implements NewStepHandler {

    public static final String OPERATION_NAME = "server-operation-resolver";

    private final String localHostName;
    private final ServerOperationResolver resolver;
    private final ParsedOp parsedOp;
    private final ModelNode response;
    private final boolean recordResponse;

    ServerOperationsResolverHandler(final String localHostName, final ServerOperationResolver resolver, final ParsedOp parsedOp,
                                    final ModelNode response, final boolean recordResponse) {
        this.localHostName = localHostName;
        this.resolver = resolver;
        this.parsedOp = parsedOp;
        this.response = response;
        this.recordResponse = recordResponse;
    }

    @Override
    public void execute(NewOperationContext context, ModelNode operation) throws OperationFailedException {

        if (!response.has(FAILURE_DESCRIPTION)) {
            final ModelNode domainModel = context.readModel(PathAddress.EMPTY_ADDRESS);
            final ModelNodeRegistration rootRegistration = context.getModelNodeRegistration();
            ParsedOp.ServerOperationProvider provider = new ParsedOp.ServerOperationProvider() {

                @Override
                public Map<Set<ServerIdentity>, ModelNode> getServerOperations(ModelNode domainOp, PathAddress address) {
                    return ServerOperationsResolverHandler.this.getServerOperations(domainOp, address, domainModel, domainModel.get(HOST).get(localHostName), rootRegistration);
                }
            };

            Map<Set<ServerIdentity>, ModelNode> serverOps = parsedOp.getServerOps(provider);
            createOverallResult(serverOps);
            if (recordResponse) {
                if (response.has(RESULT)) {
                    context.getResult().set(response.get(RESULT));
                }
                if (response.has(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(response.get(FAILURE_DESCRIPTION));
                }
            }
        } else if (recordResponse) {
            context.getFailureDescription().set(response.get(FAILURE_DESCRIPTION));
        }

        context.completeStep();
    }

    private Map<Set<ServerIdentity>, ModelNode> getServerOperations(ModelNode domainOp, PathAddress domainOpAddress, ModelNode domainModel, ModelNode hostModel, ModelNodeRegistration rootRegistration) {
        Map<Set<ServerIdentity>, ModelNode> result = null;
        Set<OperationEntry.Flag> flags = rootRegistration.getOperationFlags(domainOpAddress, domainOp.require(OP).asString());
        if (!flags.contains(OperationEntry.Flag.READ_ONLY)) {
            result = Collections.emptyMap();
        }
        if (result == null) {
            result = resolver.getServerOperations(domainOp, domainOpAddress, domainModel, hostModel);
        }
        return result;
    }

    private void createOverallResult(Map<Set<ServerIdentity>, ModelNode> serverOps) {
        ModelNode resultNode = response.get(RESULT);

        ModelNode overallResult = new ModelNode();
        overallResult.get(OUTCOME).set(SUCCESS);
        ModelNode domainResult = parsedOp.getFormattedDomainResult(resultNode);
        overallResult.get(RESULT, DOMAIN_RESULTS).set(domainResult);
        ModelNode serverOpsNode = overallResult.get(RESULT, SERVER_OPERATIONS);
        for (Map.Entry<Set<ServerIdentity>, ModelNode> entry : serverOps.entrySet()) {
            ModelNode setNode = serverOpsNode.add();
            ModelNode serverNode = setNode.get("servers");
            serverNode.setEmptyList();
            for (ServerIdentity server : entry.getKey()) {
                serverNode.add(server.getServerName(), server.getServerGroupName());
            }
            setNode.get(OP).set(entry.getValue());
        }
    }
}