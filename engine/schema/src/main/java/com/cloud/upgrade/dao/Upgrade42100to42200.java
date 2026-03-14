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
package com.cloud.upgrade.dao;

import com.cloud.utils.exception.CloudRuntimeException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Upgrade42100to42200 extends DbUpgradeAbstractImpl implements DbUpgrade, DbUpgradeSystemVmTemplate {
    private static final Path NIMBLE_RESOURCE_TYPES_DIRECTORY = Paths.get("nimble", "resource-types");

    @Override
    public String[] getUpgradableVersionRange() {
        return new String[]{"4.21.0.0", "4.22.0.0"};
    }

    @Override
    public String getUpgradedVersion() {
        return "4.22.0.0";
    }

    @Override
    public InputStream[] getPrepareScripts() {
        final String scriptFile = "META-INF/db/schema-42100to42200.sql";
        final InputStream script = Thread.currentThread().getContextClassLoader().getResourceAsStream(scriptFile);
        if (script == null) {
            throw new CloudRuntimeException("Unable to find " + scriptFile);
        }

        return new InputStream[] {script};
    }

    @Override
    public void performDataMigration(Connection conn) {
//        updateSnapshotPolicyOwnership(conn);
//        updateBackupScheduleOwnership(conn);
        populateNimbleIacResourceTypes(conn);
    }

    protected void updateSnapshotPolicyOwnership(Connection conn) {
        // set account_id and domain_id in snapshot_policy table from volume table
        String selectSql = "SELECT sp.id, v.account_id, v.domain_id FROM snapshot_policy sp, volumes v WHERE sp.volume_id = v.id AND (sp.account_id IS NULL AND sp.domain_id IS NULL)";
        String updateSql = "UPDATE snapshot_policy SET account_id = ?, domain_id = ? WHERE id = ?";

        try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql);
             ResultSet rs = selectPstmt.executeQuery();
             PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {

            while (rs.next()) {
                long policyId = rs.getLong(1);
                long accountId = rs.getLong(2);
                long domainId = rs.getLong(3);

                updatePstmt.setLong(1, accountId);
                updatePstmt.setLong(2, domainId);
                updatePstmt.setLong(3, policyId);
                updatePstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to update snapshot_policy table with account_id and domain_id", e);
        }
    }

    protected void updateBackupScheduleOwnership(Connection conn) {
        // Set account_id and domain_id in backup_schedule table from vm_instance table
        String selectSql = "SELECT bs.id, vm.account_id, vm.domain_id FROM backup_schedule bs, vm_instance vm WHERE bs.vm_id = vm.id AND (bs.account_id IS NULL AND bs.domain_id IS NULL)";
        String updateSql = "UPDATE backup_schedule SET account_id = ?, domain_id = ? WHERE id = ?";

        try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql);
             ResultSet rs = selectPstmt.executeQuery();
             PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {

            while (rs.next()) {
                long scheduleId = rs.getLong(1);
                long accountId = rs.getLong(2);
                long domainId = rs.getLong(3);

                updatePstmt.setLong(1, accountId);
                updatePstmt.setLong(2, domainId);
                updatePstmt.setLong(3, scheduleId);
                updatePstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to update backup_schedule table with account_id and domain_id", e);
        }

    }

    protected void populateNimbleIacResourceTypes(Connection conn) {
        logger.info("Populating NIMBLE IaC resource types of the [CLOUD_ACCESS_MANAGEMENT] category in the database.");
        insertIacResourceType(conn, "account.yaml", "Account", "CLOUD_ACCESS_MANAGEMENT");
        insertIacResourceType(conn, "domain.yaml", "Domain", "CLOUD_ACCESS_MANAGEMENT");
        insertIacResourceType(conn, "project.yaml", "Project", "CLOUD_ACCESS_MANAGEMENT");
        insertIacResourceType(conn, "user.yaml", "User", "CLOUD_ACCESS_MANAGEMENT");

        logger.info("Populating NIMBLE IaC resource types of the [SERVICE_OFFERING] category in the database.");
        insertIacResourceType(conn, "compute-offering.yaml", "ComputeOffering", "SERVICE_OFFERING");
        insertIacResourceType(conn, "disk-offering.yaml", "DiskOffering", "SERVICE_OFFERING");
        insertIacResourceType(conn, "network-offering.yaml", "NetworkOffering", "SERVICE_OFFERING");
        insertIacResourceType(conn, "vpc-offering.yaml", "VpcOffering", "SERVICE_OFFERING");

        logger.info("Populating NIMBLE IaC resource types of the [NETWORK] category in the database.");
        insertIacResourceType(conn, "network.yaml", "Network", "NETWORK");
        insertIacResourceType(conn, "vpc.yaml", "Vpc", "NETWORK");
        insertIacResourceType(conn, "egress-firewall-rule.yaml", "EgressFirewallRule", "NETWORK");
        insertIacResourceType(conn, "firewall-rule.yaml", "FirewallRule", "NETWORK");
        insertIacResourceType(conn, "port-forwarding-rule.yaml", "PortForwardingRule", "NETWORK");
        insertIacResourceType(conn, "static-nat.yaml", "StaticNat", "NETWORK");
        insertIacResourceType(conn, "ip-address.yaml", "IpAddress", "NETWORK");
        insertIacResourceType(conn, "load-balancer.yaml", "LoadBalancer", "NETWORK");
        insertIacResourceType(conn, "load-balancer-attachment.yaml", "LoadBalancerAttachment", "NETWORK");
        insertIacResourceType(conn, "network-acl-list.yaml", "NetworkAclList", "NETWORK");
        insertIacResourceType(conn, "network-acl-rule.yaml", "NetworkAclRule", "NETWORK");

        logger.info("Populating NIMBLE IaC resource types of the [STORAGE] category in the database.");
        insertIacResourceType(conn, "volume.yaml", "Volume", "STORAGE");
        insertIacResourceType(conn, "volume-attachment.yaml", "VolumeAttachment", "STORAGE");

        logger.info("Populating NIMBLE IaC resource types of the [COMPUTE] category in the database.");
        insertIacResourceType(conn, "virtual-machine.yaml", "VirtualMachine", "COMPUTE");
        insertIacResourceType(conn, "kubernetes-cluster.yaml", "KubernetesCluster", "COMPUTE");
        insertIacResourceType(conn, "instance-group.yaml", "InstanceGroup", "COMPUTE");
        insertIacResourceType(conn, "affinity-group.yaml", "AffinityGroup", "COMPUTE");
        insertIacResourceType(conn, "auto-scaling-policy.yaml", "AutoScalingPolicy", "COMPUTE");
        insertIacResourceType(conn, "auto-scaling-vm-group.yaml", "AutoScalingVmGroup", "COMPUTE");
        insertIacResourceType(conn, "auto-scaling-vm-profile.yaml", "AutoScalingVmProfile", "COMPUTE");
        insertIacResourceType(conn, "shared-filesystem.yaml", "SharedFilesystem", "COMPUTE");
        insertIacResourceType(conn, "ssh-key-pair.yaml", "SshKeyPair", "COMPUTE");
        insertIacResourceType(conn, "user-data.yaml", "UserData", "COMPUTE");
    }

    private void insertIacResourceType(Connection conn, String fileName, String resourceName, String resourceCategory) {
        String insertResourceTypeQuery = "INSERT INTO iac_resource_types (uuid, name, category, element_content) VALUES (UUID(), ?, ?, ?)";
        String filePath = NIMBLE_RESOURCE_TYPES_DIRECTORY.resolve(fileName).toString();
        logger.debug("Inserting resource type [name: {}] in the database.", filePath);
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
             PreparedStatement preparedStatement = conn.prepareStatement(insertResourceTypeQuery)) {
            if (inputStream == null) {
                throw new Exception(String.format("[%s] file's input stream is [null].", filePath));
            }

            String resourceTypeElementContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            preparedStatement.setString(1, resourceName);
            preparedStatement.setString(2, resourceCategory);
            preparedStatement.setString(3, resourceTypeElementContent);
            preparedStatement.executeUpdate();
        } catch (SQLException exception) {
            logger.warn("Unable to insert resource type [{}] in the database. Skipping it.", filePath, exception);
        } catch (Exception exception) {
            logger.warn("Unable to read file: [{}]. Skipping it.", filePath, exception);
        }
    }
}
