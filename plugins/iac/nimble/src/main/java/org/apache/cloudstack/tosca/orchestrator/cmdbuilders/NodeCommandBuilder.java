package org.apache.cloudstack.tosca.orchestrator.cmdbuilders;

import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.tosca.model.ToscaNodeTemplate;

public interface NodeCommandBuilder {
    BaseCmd buildProvisioningCommand(ToscaNodeTemplate toscaNodeTemplate);
    BaseCmd buildRollbackCommand(ToscaNodeTemplate toscaNodeTemplate);
}
