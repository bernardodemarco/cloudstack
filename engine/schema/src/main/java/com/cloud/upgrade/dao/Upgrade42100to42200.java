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

import com.cloud.utils.FileUtil;
import com.cloud.utils.exception.CloudRuntimeException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class Upgrade42100to42200 extends DbUpgradeAbstractImpl implements DbUpgrade, DbUpgradeSystemVmTemplate {
    private static final String NIMBLE_RESOURCE_TYPES_DIRECTORY = Paths.get("nimble", "resource-types").toString();

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
        String insertResourceTypeQuery = "INSERT INTO iac_resource_types (uuid, name, element_content) VALUES (UUID(), ?, ?)";

        List<String> filePaths = FileUtil.getFilesPathsUnderResourceDirectory(NIMBLE_RESOURCE_TYPES_DIRECTORY);
        logger.info("Found the following NIMBLE's resource types files: [{}]. " +
                "Each one of them will be iterated and its corresponding content will be inserted in the database.", filePaths);
        for (String filePath : filePaths) {
            try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
                 PreparedStatement preparedStatement = conn.prepareStatement(insertResourceTypeQuery)) {
                if (inputStream == null) {
                    throw new Exception(String.format("[%s] file's input stream is [null].", filePath));
                }

                String resourceTypeElementContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                preparedStatement.setString(1, getIacResourceTypeNameFromFilePath(filePath));
                preparedStatement.setString(2, resourceTypeElementContent);
                preparedStatement.executeUpdate();
            } catch (SQLException exception) {
                logger.warn("Unable to insert resource type [{}] in the database. Skipping it.", filePath, exception);
            } catch (Exception exception) {
                logger.warn("Unable to read file: [{}]. Skipping it.", filePath, exception);
            }
        }
    }

    protected String getIacResourceTypeNameFromFilePath(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString();
        int fileExtensionDelimiterPosition = fileName.lastIndexOf('.');
        String resourceNameInKebabCase = fileExtensionDelimiterPosition == -1 ?
                fileName : fileName.substring(0, fileExtensionDelimiterPosition);
        StringBuilder resourceNameInCamelCase = new StringBuilder();
        for (String resourceNamePart : resourceNameInKebabCase.split("-")) {
            if (!resourceNamePart.isEmpty()) {
                resourceNameInCamelCase.append(Character.toUpperCase(resourceNamePart.charAt(0)))
                        .append(resourceNamePart.substring(1));
            }
        }

        return resourceNameInCamelCase.toString();
    }
}
