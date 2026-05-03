-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

--;
-- Schema upgrade from 4.21.0.0 to 4.22.0.0
--;


-- NIMBLE
CREATE TABLE IF NOT EXISTS `cloud`.`iac_resource_types` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `uuid` VARCHAR(40) UNIQUE,
    `name` VARCHAR(100) NOT NULL COMMENT 'Profile''s element name.',
    `category` VARCHAR(100) NOT NULL COMMENT 'Profile''s element category.',
    `content` TEXT NOT NULL COMMENT 'Profile''s element content.',
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `cloud`.`iac_templates` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `uuid` VARCHAR(40) NOT NULL UNIQUE,
    `name` VARCHAR(2048) NOT NULL,
    `description` VARCHAR(4096),
    `iac_template_content` TEXT NOT NULL,
    `recursive_domains` TINYINT(1) NOT NULL DEFAULT 0,
    `domain_id` BIGINT(20) UNSIGNED NOT NULL,
    `account_id` BIGINT(20) UNSIGNED NOT NULL,
    `created` DATETIME NOT NULL,
    `removed` DATETIME,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_iac_templates__domain_id` FOREIGN KEY (`domain_id`) REFERENCES `domain`(`id`),
    CONSTRAINT `fk_iac_templates__account_id` FOREIGN KEY (`account_id`) REFERENCES `account`(`id`)
);

CREATE TABLE IF NOT EXISTS `cloud`.`iac_template_account_map` (
    `iac_template_id` BIGINT(20) UNSIGNED NOT NULL,
    `account_id` BIGINT(20) UNSIGNED NOT NULL,
    PRIMARY KEY (`iac_template_id`, `account_id`),
    CONSTRAINT `fk_iac_template_account_map__iac_template_id` FOREIGN KEY (`iac_template_id`) REFERENCES `iac_templates`(`id`),
    CONSTRAINT `fk_iac_template_account_map__account_id` FOREIGN KEY (`account_id`) REFERENCES `account`(`id`)
);

CREATE TABLE IF NOT EXISTS `cloud`.`iac_template_domain_map` (
    `iac_template_id` BIGINT(20) UNSIGNED NOT NULL,
    `domain_id` BIGINT(20) UNSIGNED NOT NULL,
    PRIMARY KEY (`iac_template_id`, `domain_id`),
    CONSTRAINT `fk_iac_template_domain_map__iac_template_id` FOREIGN KEY (`iac_template_id`) REFERENCES `iac_templates`(`id`),
    CONSTRAINT `fk_iac_template_domain_map__domain_id` FOREIGN KEY (`domain_id`) REFERENCES `domain`(`id`)
);
