package org.apache.cloudstack.tosca.functions;

import java.util.List;

public class ToscaBooleanFunctions {
    public static class ValidValues implements ToscaFunction.ToscaBooleanFunction {
        private final List<Object> validValues;

        public ValidValues(List<Object> validValues) {
            this.validValues = validValues;
        }

        @Override
        public boolean evaluate(Object value) {
            return validValues.contains(value);
        }
    }
}
