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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ToscaParsingErrorsContext {
    public static class Message {
        private final String message;
        private final String context;

        public Message(String message, String context) {
            this.message = message;
            this.context = context;
        }

        @Override
        public String toString() {
            return String.format("[ERROR - %s]: %s", context, message);
        }
    }

    private final List<Message> messages = new ArrayList<>();

    public void addError(String message, String context) {
        messages.add(new Message(message, context));
    }

    public boolean hasErrors() {
        return !messages.isEmpty();
    }

    public String buildErrorMessages() {
        return messages.stream().map(Message::toString).collect(Collectors.joining("\n"));
    }
}
