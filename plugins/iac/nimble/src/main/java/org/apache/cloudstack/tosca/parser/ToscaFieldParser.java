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
package org.apache.cloudstack.tosca.parser;

import org.apache.cloudstack.tosca.functions.ToscaBooleanFunctions;
import org.apache.cloudstack.tosca.functions.ToscaFunction;
import org.apache.cloudstack.tosca.model.ToscaAttributeDefinition;
import org.apache.cloudstack.tosca.model.ToscaCollectionType;
import org.apache.cloudstack.tosca.model.ToscaDataTypeDefinition;
import org.apache.cloudstack.tosca.model.ToscaFieldDefinition;
import org.apache.cloudstack.tosca.model.ToscaPrimitiveType;
import org.apache.cloudstack.tosca.model.ToscaPropertyDefinition;
import org.apache.cloudstack.tosca.model.ToscaTypeDefinition;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToscaFieldParser {
    private final Logger logger = LogManager.getLogger(ToscaFieldParser.class);

    public enum TypeOfToscaField {
        ATTRIBUTE, PROPERTY
    }

    /**
     * Parses a field (property or attribute) of a TOSCA resource.
     * @param rawFields The body of the field. Example: "{ name: { type: string, description: Name } }"
     * @param typeOfToscaField The type of the field that will be parsed.
     * @param dataTypes Available data types. If null, then the type of the fields must be a primitive or a collection.
     * @return a map of {@link ToscaFieldDefinition} representing the fields, whose key is the name of the field and whose value is the field itself.
     */
    protected Map<String, ? extends ToscaFieldDefinition> parseField(Object rawFields, TypeOfToscaField typeOfToscaField, Map<String, ToscaDataTypeDefinition> dataTypes) {
        Map<String, Object> fields = ToscaYamlHelper.asMap(rawFields);
        logger.debug("Parsing the following {}: {}.",
                () -> typeOfToscaField == TypeOfToscaField.ATTRIBUTE ? "attributes" : "properties", fields::keySet);

        return fields.entrySet().stream().map((field) -> {
            String name = field.getKey();
            Map<String, Object> fieldBody = ToscaYamlHelper.asMap(field.getValue());
            String description = ToscaYamlHelper.asString(fieldBody.get(ToscaConstants.DESCRIPTION));
            ToscaTypeDefinition type = parseType(fieldBody, dataTypes);

            if (typeOfToscaField == TypeOfToscaField.ATTRIBUTE) {
                ToscaAttributeDefinition attributeDefinition = new ToscaAttributeDefinition(name, description, type);
                logger.debug("Successfully parsed the following attribute: [{}].", attributeDefinition::toString);
                return attributeDefinition;
            }

            boolean required = ToscaYamlHelper.asBoolean(fieldBody.get(ToscaConstants.REQUIRED));
            ToscaFunction.ToscaBooleanFunction validation = parseToscaBooleanFunction(ToscaYamlHelper.asMap(fieldBody.get(ToscaConstants.VALIDATION)));
            String apiParameter = ObjectUtils.defaultIfNull(parseMetadata(fieldBody.get(ToscaConstants.METADATA)), name);
            ToscaPropertyDefinition propertyDefinition = new ToscaPropertyDefinition(name, description, type, required, validation, apiParameter);
            logger.debug("Successfully parsed the following property: [{}].", propertyDefinition::toString);
            return propertyDefinition;
        }).collect(Collectors.toMap(ToscaFieldDefinition::getName, (field) -> field));
    }

    /**
     * Parses the type of TOSCA resource. Types can be primitive, collection, or data type, and are represented by the {@link ToscaTypeDefinition} class.
     * @param typeBody The body of the type. Example: "{ type: list, entry_schema: { type: NodeOffering } }"
     * @param dataTypes The available data types. If null, then the type being parsed must be either a primitive or a collection.
     * @return a {@link ToscaTypeDefinition} representing the type of the field. Null if the type is not recognized.
     */
    protected ToscaTypeDefinition parseType(Map<String, Object> typeBody, Map<String, ToscaDataTypeDefinition> dataTypes) {
        String rawType = ToscaYamlHelper.asString(typeBody.get(ToscaConstants.TYPE));
        logger.debug("Parsing the following type: [{}].", rawType);
        ToscaPrimitiveType primitiveType = EnumUtils.getEnumIgnoreCase(ToscaPrimitiveType.class, rawType);
        if (primitiveType != null) {
            logger.debug("The type is a primitive, returning its corresponding ToscaTypeDefinition.");
            return ToscaTypeDefinition.ofPrimitive(primitiveType);
        }

        ToscaCollectionType collectionType = EnumUtils.getEnumIgnoreCase(ToscaCollectionType.class, rawType);
        if (collectionType != null) {
            logger.debug("The type is a collection, returning its corresponding ToscaTypeDefinition.");
            return ToscaTypeDefinition.ofCollection(collectionType, parseType(ToscaYamlHelper.asMap(typeBody.get(ToscaConstants.ENTRY_SCHEMA)), dataTypes));
        }

        if (MapUtils.isEmpty(dataTypes) || !dataTypes.containsKey(rawType)) {
            logger.debug("The [{}] type is not recognized, returning null.", rawType);
            return null;
        }

        logger.debug("The type is a data type, returning its corresponding ToscaTypeDefinition based in the declared data types: {}.", dataTypes::keySet);
        return ToscaTypeDefinition.ofDataType(dataTypes.get(rawType));
    }

    /**
     * Parses a TOSCA boolean ({@link ToscaFunction.ToscaBooleanFunction}) function.
     * @param validationBody The body of the validation field. Example: "{ $valid_values: [ $value, [ CloudManaged, ExternalManaged ] ] }")
     * @return a {@link ToscaFunction.ToscaBooleanFunction} representing the boolean function. If the function is not recognized, returns null.
     */
    protected ToscaFunction.ToscaBooleanFunction parseToscaBooleanFunction(Map<String, Object> validationBody) {
        if (MapUtils.isEmpty(validationBody)) {
            return null;
        }

        Map.Entry<String, Object> function = validationBody.entrySet().iterator().next();
        String name = function.getKey();
        Object arguments = function.getValue();
        logger.debug("Parsing the following TOSCA boolean function: [{}].", name);
        switch (name) {
            case ToscaConstants.VALID_VALUES_FUNCTION:
                return parseValidValuesFunction(arguments);
        }
        return null;
    }

    /**
     * Parses the $valid_values TOSCA function.
     * @param arguments The arguments of the $valid_values function. Example: "{ $valid_values: [ $value, [ CloudManaged, ExternalManaged ] ] }"
     * @return a {@link ToscaBooleanFunctions.ValidValues} (typed as {@link ToscaFunction.ToscaBooleanFunction}) representing the $valid_values function.
     */
    private ToscaFunction.ToscaBooleanFunction parseValidValuesFunction(Object arguments) {
        List<Object> args = (List<Object>) arguments;
        List<Object> validValues = (List<Object>) args.get(1);
        return new ToscaBooleanFunctions.ValidValues(validValues);
    }

    private String parseMetadata(Object metadataBody) {
        Map<String, Object> metadata = ToscaYamlHelper.asMap(metadataBody);
        return ToscaYamlHelper.asString(metadata.get(ToscaConstants.API_PARAMETER));
    }
}
