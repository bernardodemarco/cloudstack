// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.fixtures;

import org.apache.cloudstack.tosca.functions.ToscaBooleanFunctions;
import org.apache.cloudstack.tosca.functions.ToscaFunction;
import org.apache.cloudstack.tosca.model.ToscaAttributeDefinition;
import org.apache.cloudstack.tosca.model.ToscaCollectionType;
import org.apache.cloudstack.tosca.model.ToscaDataTypeDefinition;
import org.apache.cloudstack.tosca.model.ToscaInputDefinition;
import org.apache.cloudstack.tosca.model.ToscaNodeType;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;

import java.util.List;
import java.util.Map;

public class ToscaFixtures {
    public static Map<String, ToscaNodeType> getToscaProfileForTests() {
        ToscaNodeType vm = getVmNodeTypeForTests();
        ToscaNodeType sshKeyPair = getSshKeyPairTypeForTests();
        return Map.of(vm.getName(), vm, sshKeyPair.getName(), sshKeyPair);
    }

    private static ToscaNodeType getVmNodeTypeForTests() {
        ToscaFunction.ToscaBooleanFunction validTypes = new ToscaBooleanFunctions.ValidValues(List.of("SSVM", "VR", "CPVM"));
        ToscaPropertyDefinition type = new ToscaPropertyDefinition("type", "Type of system VM.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), true, validTypes, "type");
        ToscaPropertyDefinition vcpus = new ToscaPropertyDefinition("vcpus", "Number of vCPUs.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.INTEGER), true, null, "vcpus");
        ToscaPropertyDefinition startVm = new ToscaPropertyDefinition("start-vm", "Start VM.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.BOOLEAN), false, null, "startvm");
        ToscaPropertyDefinition maxUsage = new ToscaPropertyDefinition("max-usage", "Maximum usage.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.FLOAT), false, null, "maxusage");
        ToscaPropertyDefinition sshKeyPairId = new ToscaPropertyDefinition("ssh-key-pair-id", "SSH key pair ID.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), false, null, "sshkeypairid");
        ToscaPropertyDefinition sshKeyPairName = new ToscaPropertyDefinition("ssh-key-pair-name", "SSH key pair name.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), false, null, "sshkeypairname");

        ToscaPropertyDefinition nameDataTypeProperty = new ToscaPropertyDefinition("name", "Name.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), true, null, "name");
        ToscaPropertyDefinition valueDataTypeProperty = new ToscaPropertyDefinition("value", "Value.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), false, null, "value");
        ToscaDataTypeDefinition nameValueMappingDataType = new ToscaDataTypeDefinition("NameValueMapping", Map.of(nameDataTypeProperty.getName(), nameDataTypeProperty, valueDataTypeProperty.getName(), valueDataTypeProperty));
        ToscaPropertyDefinition nameValueMappingList = new ToscaPropertyDefinition("name-value-mapping", "Name value mapping.", ToscaTypeDefinition.ofCollection(ToscaCollectionType.LIST, ToscaTypeDefinition.ofDataType(nameValueMappingDataType)), false, null, "namevaluemapping");
        ToscaPropertyDefinition nameValueSingleMap = new ToscaPropertyDefinition("name-value-single-map", "Name value single map.", ToscaTypeDefinition.ofDataType(nameValueMappingDataType), false, null, "namevaluesinglemap");

        ToscaPropertyDefinition ipAddresses = new ToscaPropertyDefinition("ip-addresses", "IPs.", ToscaTypeDefinition.ofCollection(ToscaCollectionType.LIST, ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING)), false, null, "apiaddresses");
        ToscaPropertyDefinition details = new ToscaPropertyDefinition("offering-details", "Offering details.", ToscaTypeDefinition.ofCollection(ToscaCollectionType.MAP, ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING)), false, null, "offeringdetails");
        ToscaAttributeDefinition uuid = new ToscaAttributeDefinition("uuid", "UUID of the system VM.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), "uuid");

        return new ToscaNodeType("Vm",
                Map.of(type.getName(), type, vcpus.getName(), vcpus, startVm.getName(), startVm,
                        maxUsage.getName(), maxUsage, sshKeyPairId.getName(), sshKeyPairId, sshKeyPairName.getName(), sshKeyPairName,
                        nameValueMappingList.getName(), nameValueMappingList, nameValueSingleMap.getName(), nameValueSingleMap,
                        ipAddresses.getName(), ipAddresses, details.getName(), details),
                Map.of(uuid.getName(), uuid), "createApi", "deleteApi");
    }

    private static ToscaNodeType getSshKeyPairTypeForTests() {
        ToscaPropertyDefinition name = new ToscaPropertyDefinition("name", "Name of the SSH key pair.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), true, null, "name");
        ToscaPropertyDefinition publicKey = new ToscaPropertyDefinition("public-key", "Public key of the SSH key pair.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), true, null, "publickey");

        ToscaAttributeDefinition uuid = new ToscaAttributeDefinition("uuid", "UUID of the key pair.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), "uuid");
        return new ToscaNodeType("SshPair", Map.of(name.getName(), name, publicKey.getName(), publicKey), Map.of(uuid.getName(), uuid), "registerSSHKeyPair", "deleteSSHKeyPair");
    }

    public static Map<String, ToscaInputDefinition> getToscaInputsForTests() {
        ToscaInputDefinition vmType = new ToscaInputDefinition("vm-type", "VM type.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), "SSVM", null);
        ToscaInputDefinition publicKey = new ToscaInputDefinition("public-key", "SSH pair public key.", ToscaTypeDefinition.ofPrimitive(ToscaPrimitiveType.STRING), null, null);
        return Map.of(vmType.getName(), vmType, publicKey.getName(), publicKey);
    }
}
