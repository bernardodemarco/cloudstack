package org.apache.cloudstack.tosca.orchestrator.commandbuilders;

import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;

@FunctionalInterface
public interface NodeCommandBuilder {
    BaseCmd buildCommand(ToscaNodeTemplate toscaNodeTemplate);
}
